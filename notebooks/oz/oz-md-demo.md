

# This is an Oz Markdown document

Its mostly just **Markdown**

But you can also embed custom html as **EDN**, with a markdown code block like this

    ```edn hiccup
    [:div
      {:style {:padding 30}}
      [:h2 "hello"]
      [:md "some more **markdown**"]]
    ```

```edn hiccup
[:div
  {:style {:padding 30}}
  [:h2 "hello"]
  [:md "some more **markdown**"]]
```

You can also embed data visualizations in your code blocks using vega-lite or vega, like this

    ```edn vega-lite
    {:data {:values [{:a 1 :b 2} {:a 3 :b 3} {:a 4 :b -1}]}
     :mark :point
     :encoding {:x {:field :a}
                :y {:field :b}}}
    ```

```edn vega-lite
{:data {:values [{:a 1 :b 2} {:a 3 :b 3} {:a 4 :b -1}]}
 :mark :point
 :encoding {:x {:field :a}
            :y {:field :b}}}
```

Need to still make it possible to evaluate clojure or other code (JSX?) in these blocks.
Currently, only literal hiccup blocks are supported, but work is underway to extend this.
