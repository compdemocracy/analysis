#!/bin/sh

echo "Starting Jupyter notebook"
jupyter notebook --allow-root --ip 0.0.0.0 --port=3870 &
echo "Starting Clojure repl"
clojure -M:cider-nrepl
