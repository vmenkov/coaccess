#!/bin/bash

#------------------------------------------------------------------------
# This is the main script for updating the coaccess data. The coaccess
# matrices are generated based on arxiv.org usage data in /data/json/usage;
# the resulting data files are installed in /data/coaccess/round5.
#
# The script uses the local My.ArXiv's Lucene data store to obtain a
# (nearly) complete list of ArXiv article IDs. This dependence on
# My.ArXiv could be avoided, of course, by identifying all article IDs
# occurring in the usage data instead, but we don't bother doing that.
# ------------------------------------------------------------------------



echo `date` " : Listing valid article IDs"
cmd=../../../arxiv/cmd.sh

if [ ! -x $cmd ] ; then
    echo "Script $cmd does not exist, or is not executable. Exiting!"
    exit 1
fi

build=`pwd`/../build
aidList=`pwd`/aid-list.txt
aidListShort=`pwd`/aid-list-short.txt

echo "Running script $cmd ; output goes to $aidList"

$cmd list | sort > $aidList
grep '^15' $aidList > $aidListShort

echo "Prepared article ID list, as per My.ArXiv's Lucene data store: "
ls -l $aidList  $aidListShort
wc $aidList  $aidListShort

#-- location of the python interpretert on orie hosts; may be different elsewhere
python=/usr/local/epd/bin/python
if [ ! -e  $python ] ; then
    python=python
fi
echo "Trying to invoke python as $python"

parser=parser_vFinal.py
# parser=parser-tmp.py

outdir=/data/coaccess/round5

curYear=`(cd  /data/json/usage; ls -d 2??? | tail -1)`
month=`date +'%m'`

echo "Assuming that the current year is $curYear, month is $month"

if [ "$month"=="02" ] ; then
    prevYear=`(cd  /data/json/usage; ls -d 2??? | tail -2|head -1)`
    years="$prevYear:$curYear"
else
    years=$curYear
fi

echo `date` " : Running python script $parser for years=$years, output goes to $outdir (including per-year dirs)"



time $python $parser $years $outdir  $aidList

#echo "Dry run; exiting now"
#exit



# Lucene indexing...

cd $outdir/lucene_framework

if [ -e index.new ] ; then
    echo "Directory index.new already exists. Will not run re-indexing now. Please delete index.new and run re0indexing manually!"
    exit 1
fi

echo `date` : " : Creating Lucene Index"

#-- no -update  needed, as we use a clean dir

cp=$build/osmot-1.0.jar:lucene-analyzers-common-4.5.1.jar:lucene-demo-4.5.1-SNAPSHOT.jar:lucene-core-4.5.1.jar:lucene-queryparser-4.5.1.jar  
java  -Xmx1g -cp $cp edu.cornell.cs.osmot.coaccess.IndexFiles  -docs $outdir -index index.new -aids $aidList -years 2003:$curYear

OUT=$?

echo `date` " : Indexer completed: "
ls -ld index.new
echo "Size in GB"
du -B 1GB index.new


if [ $OUT -eq 0 ];then
   echo "IndexFiles run was apparently successful"
else
   echo "IndexFiles run apparently failed (status=" $OUT "); exiting"
   exit 1
fi

if [ -e index.old ] ;  then
    echo "Removing index.old and its content"
    rm -rf index.old
fi

if [ -e index.old ] ;  then
    echo "index.old still exist; could not remove. Please take care of this manually!"
    exit 1
fi

echo "Moving index"
mv index index.old
mv index.new index


#echo `date` : " Removing .txt files from $outdir..."
# SKIP rm $outdir/*.txt



