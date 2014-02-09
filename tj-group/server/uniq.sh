#!/bin/bash


# 2003 and 2004
ls /data/coaccess/round5/2003 > 1.txt
ls /data/coaccess/round5/2004 > 2.txt
cat 1.txt 2.txt > 12.txt

sort -u 12.txt > 12s.txt


# 2005 and 2006
ls /data/coaccess/round5/2005 > 1.txt
ls /data/coaccess/round5/2006 > 2.txt
cat 1.txt 2.txt > 12.txt

sort -u 12.txt > 34s.txt


cat 12s.txt 34s.txt > 1234.txt

sort -u 1234.txt > 1234s.txt




# 2007 and 2008
ls /data/coaccess/round5/2007 > 1.txt
ls /data/coaccess/round5/2008 > 2.txt
cat 1.txt 2.txt > 12.txt

sort -u 12.txt > 12s.txt


# 2009 and 2010
ls /data/coaccess/round5/2009 > 1.txt
ls /data/coaccess/round5/2010 > 2.txt
cat 1.txt 2.txt > 12.txt

sort -u 12.txt > 34s.txt


cat 12s.txt 34s.txt > 1234.txt

sort -u 1234.txt > 5678s.txt


# merge 8 years


cat 1234s.txt 5678s.txt > t.txt

sort -u t.txt > a.txt



# 2011 and 2012
ls /data/coaccess/round5/2011 > 1.txt
ls /data/coaccess/round5/2012 > 2.txt
cat 1.txt 2.txt > 12.txt

sort -u 12.txt > 12s.txt

ls /data/coaccess/round5/2013 > 3.txt

cat 12s.txt 3.txt > 123.txt

sort -u 123.txt > 123s.txt



# final merge
cat a.txt 123s.txt > tt.txt
sort -u tt.txt > unique.txt



cd /data/coaccess/round5
mkdir -m 777 final
cd final
cp /data/coaccess/round5/unique.txt /data/coaccess/round5/final
cd /data/coaccess/round5/final


while read p; do touch $p; done < unique.txt

rm unique.txt



# Lucene indexing...
cd /data/coaccess/round5/lucene_framework
java -cp lucene-analyzers-common-4.5.1.jar:lucene-demo-4.5.1-SNAPSHOT.jar:lucene-core-4.5.1.jar:lucene-queryparser-4.5.1.jar org.apache.lucene.demo.IndexFiles -docs /data/coaccess/round5/final/

