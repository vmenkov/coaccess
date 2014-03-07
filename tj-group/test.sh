#!/bin/csh

#-- This script is supposed to run in one's ~/arxiv/coaccess/tj-group

#-- set opt="-DOSMOT_CONFIG=$home/arxiv/arxiv"

set main=$home/arxiv/coaccess/tj-group
set lib=$main/lib
set build=$main/build

set cp="$build/osmot-1.0.jar"

ls $lib

foreach j ($lib/*.jar)
    set cp="${cp}:${j}"
end

set opt="-cp ${cp}"

#echo "opt=$opt"

echo java $opt edu.cornell.cs.osmot.coaccess.CoaccessServlet $argv 
java $opt edu.cornell.cs.osmot.coaccess.CoaccessServlet $argv 


