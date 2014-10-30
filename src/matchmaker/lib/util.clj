(ns matchmaker.lib.util
  (:require [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clj-time.core :refer [now]]
            [clj-time.format :as time-format]
            [clj-http.client :as client]
            [cheshire.core :as json])
  (:import [java.security MessageDigest]
           [com.github.jsonldjava.utils JsonUtils]))

; ----- Private functions -----

(defn- date-time-formatter
  "Format a @date-time using @formatter."
  [^org.joda.time.DateTime date-time
   formatter]
  {:pre [(keyword? formatter)]}
  (time-format/unparse (time-format/formatters formatter) date-time))

(defn- format-number
  "Returns @number formatted as float-like string
  trimmed to 2 decimal places."
  [number]
  (if (number? number)
      (format "%.2f" (double number))
      number))

; ----- Public functions -----

(declare format-date format-date-time join-file-path)

(defn append-to-uri
  "Appends @suffix to @uri, joined by a slash."
  [uri suffix]
  (str uri
       (if-not (= (last uri) \/) \/)
       suffix))

(defn avg
  "Compute average of collection @coll of numbers."
  [coll]
  (if (seq coll)
    (/ (apply + coll)
       (count coll))
    0 ; FIXME: Average of empty collection is 0.
    ))

(defn date-now
  "Returns xsd:date for the current time."
  []
  (format-date (now)))

(defn date-time-now
  "Returns xsd:dateTime for the current time."
  []
  (format-date-time (now)))

(defn deep-merge
  "Deep merge maps.
  Stolen from <https://github.com/clojure-cookbook/clojure-cookbook/blob/master/04_local-io/4-15_edn-config.asciidoc>."
  [& maps]
  (if (every? map? maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(defn exit
  "Exit with @status and message @msg"
  [^Integer status
   ^String msg]
  (println msg)
  (System/exit status))

(defn format-date
  "Encode @date-time as xsd:date"
  [date-time]
  (date-time-formatter date-time :date))

(defn format-date-time
  "Encode @date-time as xsd:dateTime"
  [date-time]
  (date-time-formatter date-time :date-time))

(defn format-numbers
  "Formats numeric values in map @m"
  [m]
  {:pre [(map? m)]}
  (reduce (fn [item [k v]] (assoc item k (format-number v))) {} m))

(defn get-int
  "Converts @string to integer.
  Returns nil if @string is not a numeric string."
  [string]
  (try (Integer/parseInt string)
       (catch NumberFormatException _)))

(defn init-logger
  "Initialize logger"
  []
  (timbre/set-config! [:appenders :standard-out :enabled?] false)
  (timbre/set-config! [:appenders :spit :enabled?] true)
  (timbre/set-config! [:shared-appender-config :spit-filename] "log/matchmaker.log"))

(defn join-file-path
  "Joins a collection representing path to file."
  [& args]
  (clojure.string/join java.io.File/separator args))

(defn lazy-cat'
  "Lazily concatenates lazy sequence of sequences @colls.
  Taken from <http://stackoverflow.com/a/26595111/385505>."
  [colls]
  (lazy-seq
    (if (seq colls)
      (concat (first colls) (lazy-cat' (next colls))))))

(defn load-jsonld-context
  "Loads JSON-LD context from @filename."
  [filename]
  (-> (join-file-path "jsonld_contexts" filename)
      io/resource
      io/reader
      json/parse-stream)) 

(defn parse-json-resource
  "Parse JSON resource on @resource-path."
  [^String resource-path]
  (-> resource-path 
      io/resource
      io/input-stream
      JsonUtils/fromInputStream)) 

(defn rescale
  "Rescale number @n from internal <@min1, @max1>
  to the interval <@min2, @max2>."
  [n [min1 max1] [min2 max2]]
  (+ min2 (/ (* (- n min1) (- max2 min2))
             (- max1 min1))))

(defn sha1
  "Computes SHA1 hash from @string."
  [^String string]
  (let [digest (.digest (MessageDigest/getInstance "SHA1") (.getBytes string))]
    ;; Stolen from <https://gist.github.com/kisom/1698245#file-sha256-clj-L19>
    (clojure.string/join (map #(format "%02x" (bit-and % 0xff)) digest))))

(defn time-difference
  "Computes time difference (in seconds) from @start-time."
  [start-time]
  (/ (- (System/nanoTime) start-time) 1e9))

(defn try-times*
  "Try @body for number of @times. If number of retries exceeds @times,
  then exception is raised. Each unsuccessful try is followed by sleep,
  which increase in length in subsequent tries."
  [times body]
  (loop [n times]
    (if-let [result (try [(body)] 
                         (catch Exception ex
                           (if (zero? n)
                             (throw ex)
                             (Thread/sleep (-> (- times n)
                                               (* 2000)
                                               (+ 1000))))))]
      (result 0)
      (recur (dec n)))))

(defn url?
  "Tests if @url is valid absolute URL."
  [url]
  (try
    (java.net.URL. url)
    (catch Exception _ false)))

(defn url-alive?
  "Pings a @url to test if it's alive."
  [url]
  (try
    (client/get url) true
    (catch Exception _ false)))

(defn uuid
  "Generates a random UUID"
  []
  (str (java.util.UUID/randomUUID)))

; ----- Macros -----

(defmacro try-times
  "Executes @body. If an exception is thrown, will retry. At most @times retries
  are done. If still some exception is thrown it is bubbled upwards in the call chain.
  Adapted from <http://stackoverflow.com/a/1879961/385505>."
  [times & body]
  `(try-times* ~times (fn [] ~@body)))
