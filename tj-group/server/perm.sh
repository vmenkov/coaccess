#!/bin/bash

echo `date` " : Running python script"

/usr/local/epd/bin/python parser_vFinal.py


echo `date` " : Touching new year files"

ls /data/coaccess/round5/2014 > cur.txt


#cat cur.txt pastyearUniq.txt > newyears.txt


# final merge
#sort -u newyears.txt > newyearsUniq.txt

cd /data/coaccess/round5
mkdir -m 777 newyear
cd newyear
cp /data/coaccess/round5/cur.txt /data/coaccess/round5/newyear
cd /data/coaccess/round5/newyear


while read p; do touch $p; done < cur.txt

rm cur.txt


echo `date` : " : Creating Lucene Index"

# Lucene indexing...
cd /data/coaccess/round5/lucene_framework
java -cp lucene-analyzers-common-4.5.1.jar:lucene-demo-4.5.1-SNAPSHOT.jar:lucene-core-4.5.1.jar:lucene-queryparser-4.5.1.jar org.apache.lucene.demo.IndexFiles -docs /data/coaccess/round5/final/


cd /data/coaccess/round5


echo `date` : " Removing .txt files..."

rm *.txt



