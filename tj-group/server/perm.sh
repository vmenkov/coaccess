#!/bin/bash

echo `date` " : Listing valid article IDs"
cmd=../../../arxiv/cmd.sh

if [ ! -x $cmd ] 
then
    echo "Script $cmd does not exist, or is not executable. Exiting!"
    exit
fi

build=`pwd`/../build
aidList=`pwd`/aid-list.txt
aidListShort=`pwd`/aid-list-short.txt

echo "Running script $cmd ; output goes to $aidList"

$cmd list > $aidList
grep '^14' $aidList > $aidListShort

echo "Prepared article ID list, as per My.ArXiv's Lucene data store: "
ls -l $aidList $aidListShort
wc $aidList $aidListShort

parser=parser_vFinal.py
# parser=parser-tmp.py

outdir=/data/coaccess/round5

echo `date` " : Running python script $parser, output goes to $outdir (including per-year dirs)"

time /usr/local/epd/bin/python $parser 2014 $outdir  $aidList

# exit


#echo `date` " : Touching new year files"

#cd $outdir 
#ls 2014 > cur.txt

#cat cur.txt pastyearUniq.txt > newyears.txt


# final merge
#sort -u newyears.txt > newyearsUniq.txt

#mkdir -m 777 newyear
#cd newyear
#cp $outdir/cur.txt $outdir/newyear
#cd $outdir/newyear

#while read p; do touch $p; done < cur.txt

#rm cur.txt

echo `date` : " : Creating Lucene Index"

# Lucene indexing...

cd $outdir/lucene_framework

cp=$build/osmot-1.0.jar:lucene-analyzers-common-4.5.1.jar:lucene-demo-4.5.1-SNAPSHOT.jar:lucene-core-4.5.1.jar:lucene-queryparser-4.5.1.jar  
java -cp $cp edu.cornell.cs.osmot.coaccess.IndexFiles  -docs $outdir -update -index index.new -aids $aidList -years 2003:2014

#echo `date` : " Removing .txt files from $outdir..."
# SKIP rm $outdir/*.txt



