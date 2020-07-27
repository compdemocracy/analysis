(ns polis.main
  ;; We are using gen-class here so we can AOT for Clojupyter
  (:gen-class :main true))

;; This is the main entry point for the Clojupyter compilation target.
;; It's technically a shim-class, and requires some fancy features to get things working.
;; In short though, we have to make sure these targets are dynamically loaded when the compilation target is
;; created or the won't get compiled in to the uberjar
(defn -main [& args]
  ;; clojure.set isn't imported by default, causing errors when
  ;; aot-compiling in some places.  May not be necessary here tho,
  ;; left in just in case.
  (require 'clojure.set)
  ;; Require clojupyter and the oz clojupyter plugin
  (require 'clojupyter.protocol.mime-convertible)
  (require 'oz.notebook.clojupyter)
  (binding [*ns* *ns*]
    ;; rather than :require it in the ns-decl, we load it
    ;; at runtime.
    (require 'polis.math)
    (in-ns 'polis.math)
    ;; if we don't use resolve, then we get compile-time aot
    ;; dependency on marathon.core.  This allows us to shim the
    ;; class.
    ((resolve 'polis.math/-main))))

