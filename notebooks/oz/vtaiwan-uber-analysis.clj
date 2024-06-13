(ns vtaiwan-uber-analysis
  (:require [polis.math :as math]
            [polis.viz :as viz]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.column :as dcol]
            [semantic-csv.core :as csv]))


(def raw-data
  (math/load-data "data/vtaiwan-uber-conv.ts-2015-08-28.exported-2020-07-02"))

(def data
  (-> raw-data
      (math/subset-grouped-participants)
      (math/mod-filter false)))

(def analysis
  (-> data
      (math/apply-scikit-pca {:dimensions 2})
      (math/apply-umap {})))

[:h1 "vTaiwan Uber Analysis"]

[:md
 "[Polis](https://pol.is/home) is an open source [wiki-survey](https://roamresearch.com/#/app/polis-methods/page/DZfOLUSvE)
 platform for rapid, scalable, open ended feedback, in which [participants](https://roamresearch.com/#/app/polis-methods/page/me6hHfaqb)
 submit short [comments](https://roamresearch.com/#/app/polis-methods/page/9sgrt0LbX)
 which are sent out [semi-randomly](https://roamresearch.com/#/app/polis-methods/page/vIbPEejlQ)
 to other participants to vote on (by clicking agree, disagree or pass).
 Polis uses [statistical algorithms](https://roamresearch.com/#/app/polis-methods/page/ciPWF73Ss)
 to find patterns of [consensus](https://roamresearch.com/#/app/polis-methods/page/sl2uYQN7X)
 and [opinion groups](https://roamresearch.com/#/app/polis-methods/page/iJCEaDWYA)."]

[:md
 "This report looks at the data generated in an engagement run by the government of Taiwan
 in August of 2015 concerning how Uber should be regulated in the nation.
 People's opinions, as reflected in this data, were then fed into a series of in person
 consultations with stakeholders, as part of the nation's [vTaiwan](https://info.vtaiwan.tw/)
 deliberative process, and points of consensus were used to craft legislation which was broadly
 viewed as fair to all parties (including the traditional Taxi companies and the citizens of Taiwan)."]

[:h3 "Basic statistics"]

[viz/summary-table data]

[:md
 "We can take these votes and arrange them into a matrix, where rows correspond to participants and columns correspond to statements.
 This allows us to think of participants as having positions in high dimensional space (dimensionality equal to the number of comments)."]

[:vega-lite (viz/vote-matrix analysis)]

[:h2 "Dimensionality reduction & opinion groups"]

[:md
 "While the above visualization may be impressive, it's not particularly useful as far as understanding how participants opinions relate to each other.
 To better understand this, we can apply a _dimensionality reduction_ algorithm, which allows us to capture as much of the variance within the data as we can within a lower dimensional space.
 Specifically, reducing to 2-dimensions allows us to plot participants locations in relation to each other in an _opinion space_, where participants are close together if they tend to agree, and further apart if they tend to disagree.
 Here, we're also coloring according to a K-means clustering of the participants into _opinion groups_,
 which lets us ask questions about what's important to different groups, and better understand the opinion landscape."]

[:vega-lite (viz/pca-plot analysis)]

[viz/variance-report analysis]

[:md
 "The sharp decline in explained variance from the 1st to the 2nd principal component reflects a very strong pro/con division in opinions in relation to Uber.
 However, the fact that over 70% percent of the variance is _not_ captured by these first two components suggests that there is still some structure not being revealed here."]

[:vega-lite (viz/umap-pca-comparison analysis)]
