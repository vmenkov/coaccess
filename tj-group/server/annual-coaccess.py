# Parses JSON into lists of user data. Each list contains the documents that user accessed in a given user session

import sys
import cPickle as pickle
import numpy as np
#import scipy.sparse
import gzip
import json
import itertools
import time
import os
from sets import Set
from os import walk
#from scipy.sparse import lil_matrix
#from scipy.sparse.linalg import spsolve
#from numpy.linalg import solve, norm
#from numpy.random import rand

from json import dumps, loads, JSONEncoder, JSONDecoder
import pickle
import re

if (len(sys.argv)!=4):
    print "Usage: " + sys.argv[0] + " year out-dir-name correct-aid-list-file"
    exit(1)

years = [ int(sys.argv[1]) ]
outdir=sys.argv[2]
correctAidListFile=sys.argv[3]

if ((years[0] <= 2000) or (years[0] >= 2020)):
    print "Year " + str( years[0]) + " is out of expected range"
    exit()


if (not(os.path.isdir(outdir))):
    print "Directory " + outdir + " does not exist!"
    exit()

if (not(os.path.isfile(correctAidListFile))):
    print "File " + correctAidListFile + " does not exist!"
    exit()



# Make set JSON serializable
class SetEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, set):
            return list(obj)
        return json.JSONEncoder.default(self, obj)

# constructs hashtable of only valid documents
#-- using an ArXiv ID list file produced with "~vmenkov/arxiv/arxiv/cmd.sh list"
def constructHash(openCorrect):
    correct = dict()
    os.chdir(outdir)
    
    print 'Reading list of valid article IDs from ' + openCorrect + ' ...'
    with open(openCorrect,'r+') as infile:
        for line in infile:
            cur = line.strip()
            correct[cur] = cur
        print "Has read article ID list; size=" + str(len(correct))
        return correct
    # optionally cache hashtable
    

# Returns a hash table of arrays with document accesses for a given user
def produceUserList():
    global years
    validDoc = constructHash(correctAidListFile)
    os.chdir(outdir)	
    print "Working in directory " + os.getcwd() + " . Temporary files will be created here"
    
    for year in years:
        strToFile = str(year) + "_phase1"
        h = open('%s.txt' % strToFile,'wb')        
        counter= 0;


        # Counter to print
        i = 0

        data_path = '/data/json/usage/' + str(year) + '/'
        print "Processing JSON files for Year " + str(year) + ", from the directory " + data_path;
        # Get all files under the directory
        files = []
        for (dirpath, dirnames, filenames) in walk(data_path):
            files.extend(filenames)
        for file_name in files:


#            pat = re.compile('^14010[123].*')
#            m = pat.match( file_name)
#            if not(m):
#                continue

            i += 1
            file_path = data_path + file_name
            print "Processing file("  + str(i) + ")=" + file_path;
            f = gzip.open(file_path,'rb')
            data = json.load(f)
            entries = data['entries']
            for entry in entries:
                if (('arxiv_id' in entry) and ('utc' in entry)):
		    arxiv_id = str(entry['arxiv_id'])
                    utc= str(entry['utc'])
		    if (arxiv_id in validDoc):
                        if 'cookie_hash' in entry:
                             fin_hash = str(entry['cookie_hash'])
                        elif 'ip_hash' in entry:
                            fin_hash = str(entry['ip_hash'])
                        else:
                            continue
                        h.write(str(fin_hash) + " " + str(arxiv_id) + " " + str(utc) + "\n")
        h.close()
        fakereducer(year)


#-------------------------------------------------------------------
# Removes duplicates from the list of (user, article) pairs.
# (Duplicates may occur e.g. due to the user reloading a page.
# They may or may not have the same time stamp).
#-------------------------------------------------------------------
def fakereducer(year):
    strToFile = str(year) + "_phase1.txt"
    strToSort = str(year) + "_phase2.txt"
    strToReduce = str(year) + "_phase3.txt"
    os.system('sort -k1,1 -k2,2 ' + strToFile + ' > ' + strToSort)  
    f= open(strToReduce,'w+')
    with open(strToSort,'r+') as infile:
        prev= infile.readline()
        counter= 1
        for line in infile:
            temp = line.split(" ");
            tempPrev = prev.split(" ");
            if (str(temp[0] + temp[1]) != str(tempPrev[0] + tempPrev[1])):
		prev = tempPrev[0] + " " + tempPrev[1] + " " + tempPrev[2]
		line = temp[0] + " " + temp[1] + " " + temp[2]
                f.write(prev.strip() + "\n")
                prev= line
    f.close()
    pairGeneration(year)

class PairSaver:
    #-- topmap: mapping: a -> ([b1,b2,b3], b4, [b5,b6].....)
    def __init__(self, _xDir):
        self.topmap = {}
        self.size = 0
        self.xDir = _xDir
        self.allCnt = 0
    def addPairs(self, user, currentDocs):
        n = len(currentDocs)
        if (n>200):
            print("Skip user " + user + " with " + str(n) + " article views")
            return
        elif (n<=1):
            return                    
        z = []
        for i in range (0,n):
            z.append(currentDocs[i])
        for a in (z):
            if (a in self.topmap):
                self.topmap[a].append(z)
            else:
                self.topmap[a] = [z]
        self.size += 2*len(z)
        if (self.size > 100000):
            self.writeAll()

    def writeAll(self):
        fileCnt = 0
        cnt = 0        
        print("Pair buffer flush: size="+str(self.size)+" top map keys=" + str(len(self.topmap.keys())))
        for a in (self.topmap.keys()):
            with open(self.xDir + "/" + a.replace("/","@"), 'ab') as f:
                fileCnt += 1
                for v in (self.topmap[a]):
                    for b in (v):
                        if (b != a):
                            f.write(b + "\n")
                            cnt += 1
        self.topmap.clear()
        self.size = 0
        self.allCnt += cnt
        print("Pair buffer flush: wrote " + str(cnt) + " values into " + str(fileCnt) + " files")


# Generates pairs into text file
def pairGeneration(year):
    userCnt = 0
    pairCnt = 0
    strToReduce = str(year) + "_phase3.txt"
    xDir = str(year) + "_split1"
    os.system('rm -rf ' + xDir)
    if (os.path.exists(xDir)):
        print "Directory " + xDir + " already exists and cannot be removed. Exiting"
        exit()
    os.mkdir(xDir)
    pairSaver = PairSaver(xDir);

    with open(strToReduce, 'r+') as infile:
        prevUser = None
        currentDocs= []

        for line in infile:
            userLine = line.split(" ")
            user = userLine[0]
            if (prevUser is None or user != prevUser):
                userCnt += 1
                pairSaver.addPairs(user, currentDocs)
                prevUser = user
                currentDocs= []
            currentDocs.append(userLine[1])

        pairSaver.addPairs(user, currentDocs)
        
    pairSaver.writeAll()
    print("At " + time.strftime("%c") + ", completed pair generation. In total, wrote " + str(pairSaver.allCnt) + " pairs via PairSaver");
    perArticle(xDir, str(year))

#-- sorts coaccess data in each per-article file
def perArticle(fromDir, toDir):
    print("Processing per-article files from dir " + fromDir + ", results go to " + toDir)

    os.system('rm -rf ' + toDir)
    if (os.path.exists(toDir)):   
        print "Directory " + toDir + " already exists and cannot be removed. Exiting"
        exit()
    os.mkdir(toDir)

    cutOff = 100
    files = []
    i = 0
    wroteCnt = 0

    for (dirpath, dirnames, filenames) in walk(fromDir):
        files.extend(filenames)
    for file_name in files:
        file_path = fromDir +"/" + file_name
        i += 1
        print "Processing article file("  + str(i) + ")=" + file_path;
        #-- read all lines, and put counts into h
        h = {}
        with open(file_path, 'r+') as infile:
            for line in infile:
                b= line.strip()
                if (b in h):
                    h[b] += 1
                else:
                    h[b] = 1
        bAndCnt = []
        for b in (h.keys()):
            tu = (b, h[b])
            bAndCnt.append(tu)
        bAndCnt = sorted(bAndCnt, key=lambda x: x[1], reverse=True)

        out_path = toDir +"/" + file_name
        with open( out_path, 'wb') as curFile:
            cnt = 0
            for t in bAndCnt:
                curFile.write(t[0] + " " + str(t[1]) + "\n")
                cnt += 1
                if (cnt >= cutOff):
                    break
            wroteCnt += cnt
    print "At " + time.strftime("%c") +", done processing per-article files. Wrote " + str(i) + " files, " + str(wroteCnt) + " lines"

produceUserList()


