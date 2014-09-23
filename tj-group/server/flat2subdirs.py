# This is an auxiliary script to move files from a flat directory
# structure to a depth-1 tree. 0704.0001 goes to 0704/0704.0001,
# cat@001 goes to cat/cat@001 etc.

import sys
import itertools
import time
import os
from sets import Set
from os import walk

#from json import dumps, loads, JSONEncoder, JSONDecoder
#import pickle
import re

def perYear(fromDir, toDir):
    print("Moving article files from dir " + fromDir + " to " + toDir)

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

    moveCnt = 0
    for file_name in files:


        subdir = None

        m = pat.search( file_name)
        if (not m): 
            print "Found no prefix in " + file_name + "; skip"
            continue

        prefix = m.group(1)
 #       print "found ("+prefix+") in " +file_name
 
        dest = toDir + "/" + prefix
        if (not os.path.exists(dest)):   
            os.mkdir(dest)
 
        os.rename(fromDir + "/" +file_name , dest+ "/" +file_name )
        moveCnt += 1

    print "Moved " + str(moveCnt) + " files from " + fromDir + " to " + toDir

    
if (len(sys.argv)!=3):
    print "Usage: " + sys.argv[0] + " dir1 dir2"
    exit(1)

dir1 = sys.argv[1]
dir2 = sys.argv[2]

perYear(dir1, dir2)
