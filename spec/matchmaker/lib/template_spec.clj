(ns matchmaker.lib.template-spec
  (:require [speclj.core :refer :all]
            [matchmaker.lib.template :refer :all]
            [clojure.string :refer [trim-newline]]))

(describe "render-template"
          (it "correctly renders basic template"
              (should= (trim-newline (render-template ["render-template_basic"] :variable "variable"))
                       "variable"))
          (it "correctly includes a partial template"
              (should= (render-template ["render-template_includes_partial"])
                       (render-template ["render-template_partial"]))))

(run-specs)
