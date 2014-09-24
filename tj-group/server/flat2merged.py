# This is an auxiliary script to merge files from a flat directory
# structure to a few larger files

import sys
import itertools
import time
import os
#from sets import Set
from os import walk

#from json import dumps, loads, JSONEncoder, JSONDecoder
#import pickle
import re

#-- Merges all files from a specified directory into several large files (one per cat prefix)
def perYear(fromDir, toDir):
    print("Merging article files from dir " + fromDir + " to " + toDir)

#    os.system('rm -rf ' + toDir)
    if (os.path.exists(toDir)):   
        print "Directory " + toDir + " already exists. Exiting"
        exit()
    os.mkdir(toDir)

    files = []
    i = 0
    wroteCnt = 0

    pat = re.compile('^(.*)[\.@]')

    for (dirpath, dirnames, filenames) in walk(fromDir):
        files.extend(filenames)

    files.sort()

    h = None

    oldPrefix = None

    mergeCnt = 0
    for file_name in files:

        subdir = None

        m = pat.search( file_name)
        if (not m): 
            print "Found no prefix in " + file_name + "; skip"
            continue

        prefix = m.group(1)
 #       print "found ("+prefix+") in " +file_name
 
        if (oldPrefix is None or prefix != oldPrefix):
            if (h is not None):
                h.close()
            dest = toDir + "/" + prefix + ".txt"
            print "Writing to file " + dest
            h = open(dest,'wb')
            oldPrefix = prefix

        aid = file_name.replace("@","/")
        with open(fromDir + "/" +file_name,'r') as f:
            text = ": " + aid + "\n"
            text +=  f.read()
            h.write(text)
#            h.write(": " + aid + "\n" + f.read())
            
        mergeCnt += 1

    if (h is not None):
        h.close()

    print "Merged " + str(mergeCnt) + " files from " + fromDir + " to " + toDir
    
if (len(sys.argv)!=3):
    print "Usage: " + sys.argv[0] + " dir1 dir2"
    exit(1)

dir1 = sys.argv[1]
dir2 = sys.argv[2]

perYear(dir1, dir2)
