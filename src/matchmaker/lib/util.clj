(ns matchmaker.lib.util)

; Public functions

(defn avg
  "Compute average of collection @coll of numbers."
  [coll]
  (let [coll-count (count coll)]
    (if (zero? coll-count)
      0
      (/ (apply + coll)
         (count coll)))))

(defn exit
  "Exit with @status and message @msg"
  [^Integer status
   ^String msg]
  (println msg)
  (System/exit status))

(defn join-file-path
  "Joins a collection representing path to file."
  [& args]
  (clojure.string/join java.io.File/separator args))

(defn time-difference
  "Computes time difference (in seconds) from @start-time."
  [start-time]
  (/ (- (System/nanoTime) start-time) 1e9))
