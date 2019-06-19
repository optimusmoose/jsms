#!/bin/bash
echo "points,mz5,mzML,mzdb,mzTree,mzTree-batched,mzTree-summ"
for i in {1..100}
do
  echo $(python3 ./query.py queries/${1}/$i.csv | tail -n1)
  notify-send "Done: $i"
done
