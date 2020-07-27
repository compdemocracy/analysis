
jupyter notebook --allow-root --ip 0.0.0.0 --port=3870 &
clojure -R:nREPL -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]" --port 3850


#jupyter notebook --port=3870 &
#P1=$!
#clojure -R:nREPL -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]" --port 3850 &
#P2=$!
#wait $P1 $P2

#(trap 'kill 0' SIGINT; clojure -R:nREPL -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]" &\
  #jupyter notebook --port=8890)

