#!/bin/sh

echo "Starting Jupyter notebook"
conda run --live-stream -n polis-analysis jupyter notebook --allow-root --ip 0.0.0.0 --port=3870 &
echo "Starting Clojure repl"
conda run --live-stream -n polis-analysis clojure -M:cider-nrepl

#jupyter notebook --port=3870 &
#P1=$!
#clojure -R:nREPL -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]" --port 3850 &
#P2=$!
#wait $P1 $P2

#(trap 'kill 0' SIGINT; clojure -R:nREPL -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]" &\
  #jupyter notebook --port=8890)

