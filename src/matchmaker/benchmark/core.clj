(ns matchmaker.benchmark.core
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.sparql :refer [get-count select-1-variable ->SparqlEndpoint]]
            [matchmaker.benchmark.setup :as setup]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.teardown :as teardown]
            [com.stuartsierra.component :as component]))

(declare ->Benchmark ->BenchmarkRun)

; ----- Multimethods -----

(defmulti get-limits-and-offsets
  "Returns a sequence of limits and offset for given sampling @process."
  (fn [process & _] process))

(defmethod get-limits-and-offsets :n-fold-cross-validation
  [_ & {:keys [contract-count number-of-runs]}]
  (let [; int rounds down sample sizes (limits)
        basic-limits (repeat number-of-runs (int (/ contract-count number-of-runs)))
        ; First samples will be incremented by 1 to cover the complete dataset.
        incremented-samples (mod contract-count number-of-runs) 
        sample-limits (concat (map inc (take incremented-samples basic-limits))
                              (take-last (- number-of-runs incremented-samples) basic-limits))]
    (map (fn [limit offset] {:limit limit :offset offset})
         sample-limits
         (conj (butlast (reductions + sample-limits)) 0))))

(defmethod get-limits-and-offsets :repeated-random-sampling
  [_ & {:keys [contract-count number-of-runs sample-size]}]
  (take number-of-runs (map (fn [limit offset] {:limit limit :offset offset})
                         (repeat sample-size)
                         (repeatedly #(inc (rand-int (- contract-count sample-size)))))))

; ----- Private functions -----

(defn- count-contracts
  "Get count of relevant contracts"
  [sparql-endpoint sample-criteria]
  {:pre [(map? sample-criteria)
         (:min-additional-object-count sample-criteria)
         (:min-main-object-count sample-criteria)]
   :post [(pos? %)]}
  (-> (select-1-variable sparql-endpoint
                         :count
                         ["benchmark" "setup" "count_contracts"]
                         :data sample-criteria)
      first
      Integer.))

(defn- load-benchmark
  "Setup benchmark system with its dependencies."
  [number-of-runs]
  (let [config-file-path (:matchmaker-config env)
        benchmark-system (component/system-map
                           :config (->Config config-file-path)
                           :sparql-endpoint (component/using (->SparqlEndpoint) [:config])
                           :benchmark (component/using (->Benchmark number-of-runs)
                                                       [:config :sparql-endpoint]))]
    (component/start benchmark-system)))

(defn- load-benchmark-run
  "Setup single benchmark run with its dependencies."
  [benchmark & {:keys [limit offset]}]
  (component/start (component/system-map :benchmark benchmark
                                         :benchmark-run (component/using (->BenchmarkRun limit offset)
                                                                         [:benchmark]))))

; ----- Records -----

(defrecord Benchmark [number-of-runs]
  component/Lifecycle
  (start [{{{:keys [data-reduction process sample]} :benchmark} :config
           :keys [sparql-endpoint]
           :as benchmark}]
    (let [contract-count (count-contracts sparql-endpoint sample)
          data-reduced? (< data-reduction 1)]
      (timbre/debug (format "Starting benchmark using %s with %d runs."
                            (name process)
                            number-of-runs))
      (setup/sufficient-data? sparql-endpoint)
      (setup/single-winner? sparql-endpoint)
      (when data-reduced?
        (timbre/debug (format "Reducing data to %s %%." (* data-reduction 100)))
        (setup/reduce-data sparql-endpoint contract-count data-reduction))
      (assoc benchmark
             :bidders-count (if data-reduced?
                              ; If data was reduced, we need to re-COUNT bidders.
                              (get-count sparql-endpoint ["count_business_entities"])
                              (get-in sparql-endpoint [:counts :business-entities]))
             :limits-and-offsets (get-limits-and-offsets
                                   process
                                   :contract-count (if data-reduced?
                                                     ; If data was reduced, we need to
                                                     ; recompute the contract count.
                                                     (count-contracts sparql-endpoint sample)
                                                     contract-count)
                                   :number-of-runs number-of-runs
                                   :sample-size (:size sample)))))
  (stop [{{{:keys [data-reduction]} :benchmark} :config
          :keys [sparql-endpoint]
          :as benchmark}]
    (timbre/debug "Stopping benchmark...")
    (when (< data-reduction 1)
      (timbre/debug "Returning back reduced data.") 
      (teardown/return-reduced-data sparql-endpoint))
    benchmark))

(defrecord BenchmarkRun [limit offset]
  component/Lifecycle
  (start [{{:keys [sparql-endpoint]} :benchmark
           :as benchmark-run}]
    (timbre/debug "Starting benchmark's run...")
    (try (setup/load-contracts sparql-endpoint
                               :limit limit
                               :offset offset)
         (setup/delete-awarded-tenders sparql-endpoint)
         (assoc benchmark-run :correct-matches (setup/load-correct-matches sparql-endpoint))
         ; Stop the benchmark's run in case an exception is thrown.
         ; This returns the withheld data and clears the benchmark graph.
         (catch Exception ex
           (component/stop benchmark-run)
           (throw ex))))
  (stop [{{:keys [sparql-endpoint]} :benchmark
          :as benchmark-run}]
    (timbre/debug "Stopping benchmark's run...")
    (teardown/return-awarded-tenders sparql-endpoint)
    (teardown/clear-graph sparql-endpoint)
    benchmark-run))

; ----- Public functions -----

(defn do-single-run
  "Run benchmark once for @matchmaker using @matchmaking-endpoint."
  [benchmark matchmaking-endpoint matchmaker & {:keys [limit offset]}]
  (let [benchmark-run (component/start (assoc (->BenchmarkRun limit offset)
                                              :benchmark benchmark))] 
    (try (doall (evaluate/evaluate-rank benchmark-run
                                        matchmaking-endpoint
                                        matchmaker))
         (finally (component/stop benchmark-run)))))

(defn run-benchmark
  "Run benchmark of @matchmaker on @matchmaking-endpoint for @number-of-runs."
  [matchmaking-endpoint matchmaker number-of-runs]
  {:pre [(string? matchmaking-endpoint)
         (string? matchmaker)
         (number? number-of-runs)]}
  (let [benchmark (load-benchmark number-of-runs)
        single-run-fn (fn [{:keys [limit offset]}]
                       (do-single-run benchmark
                                      matchmaking-endpoint
                                      matchmaker
                                      :limit limit
                                      :offset offset))]
    (try (->> (get-in benchmark [:benchmark :limits-and-offsets])
              (map single-run-fn)
              (evaluate/postprocess-results (:benchmark benchmark))
              doall)
         (finally (component/stop benchmark)))))
