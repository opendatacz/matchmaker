(ns matchmaker.lib.util
  (:require [taoensso.timbre :as timbre]
            [clj-time.core :refer [now]]
            [clj-time.format :as time-format]
            [cheshire.core :as json])
  (:import [java.security MessageDigest]))

; ----- Private functions -----

(defn- date-time-formatter
  "Format a @date-time using @formatter."
  [^org.joda.time.DateTime date-time
   formatter]
  {:pre [(keyword? formatter)]}
  (time-format/unparse (time-format/formatters formatter) date-time))

; ----- Public functions -----

(declare format-date-time
         join-file-path)

(defn append-to-uri
  "Appends @suffix to @uri, joined by a slash."
  [uri suffix]
  (str uri
       (if-not (= (last uri) \/) \/)
       suffix))

(defn avg
  "Compute average of collection @coll of numbers."
  [coll]
  (when-not (empty? coll)
    (/ (apply + coll)
       (count coll))))

(defn date-time-now
  "Returns xsd:dateTime for current time."
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

(defn get-int
  "Converts @string to integer.
  Returns nil if @string is not a numeric string."
  [string]
  (try (Integer/parseInt string)
       (catch NumberFormatException _)))

(defn init-logger
  "Initialize logger"
  []
  (do ; Disable output to STDOUT
    (timbre/set-config! [:appenders :standard-out :enabled?] false)
    (timbre/set-config! [:appenders :spit :enabled?] true)
    (timbre/set-config! [:shared-appender-config :spit-filename] (join-file-path "log" "logger.log"))))

(defn join-file-path
  "Joins a collection representing path to file."
  [& args]
  (clojure.string/join java.io.File/separator args))

(defn load-jsonld-context
  "Loads JSON-LD context from @filename."
  [filename]
  (-> (join-file-path "jsonld_contexts" filename)
      clojure.java.io/resource
      clojure.java.io/reader
      json/parse-stream)) 

(defn sha1
  "Computes SHA1 hash from @string."
  [string]
  (let [digest (.digest (MessageDigest/getInstance "SHA1") (.getBytes string))]
    ;; Stolen from <https://gist.github.com/kisom/1698245#file-sha256-clj-L19>
    (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))

(defn time-difference
  "Computes time difference (in seconds) from @start-time."
  [start-time]
  (/ (- (System/nanoTime) start-time) 1e9))

(defn url?
  "Tests if @url is valid absolute URL."
  [url]
  (try
    (java.net.URL. url)
    (catch Exception e false)))
