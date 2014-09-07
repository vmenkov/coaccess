# Parses JSON into lists of user data. Each list contains the documents that user accessed in a given user session

import sys
import cPickle as pickle
import numpy as np
import scipy.sparse
import gzip
import json
import itertools
import time
import os
from sets import Set
from os import walk
from scipy.sparse import lil_matrix
from scipy.sparse.linalg import spsolve
from numpy.linalg import solve, norm
from numpy.random import rand

from json import dumps, loads, JSONEncoder, JSONDecoder
import pickle
import re

if (len(sys.argv)!=4):
    print "Usage: " + sys.argv[0] + " year out-dir-name correct-aid-list-file"
    exit()

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
#    os.chdir('/data/coaccess/round5')	
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



# Reduces list of pairs for duplicates
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
	    
            if (not (container(tempPrev[1]))): tempPrev[1] = re.sub('v.*','',tempPrev[1])
            if (not (container(temp[1]))): temp[1] = re.sub('v.*', '', temp[1]);
            if (not (container(temp[0]))): temp[0] = re.sub('v.*', '', temp[0]);
            if (not (container(tempPrev[0]))): tempPrev[0] = re.sub('v.*', '', tempPrev[0]);

            if (str(temp[0] + temp[1]) != str(tempPrev[0] + tempPrev[1])):
		prev = tempPrev[0] + " " + tempPrev[1] + " " + tempPrev[2]
		line = temp[0] + " " + temp[1] + " " + temp[2]
                f.write(prev.strip() + "\n")
                prev= line
    f.close()
    pairGeneration(year)


# checks whether a substring is contained or not
def container(stringS):
    subS = 'solv-'
    return (subS in stringS)




# Generates pairs into text file
def pairGeneration(year):
    counter= 0;
    strToReduce = str(year) + "_phase3.txt"
    strToPair = str(year) + "_phase4.txt"
    pairFile = open(strToPair, 'wb')

    with open(strToReduce, 'r+') as infile:
        prev = infile.readline()
        prev = prev.split(" ")
        prevDoc = prev[1]
        prevUser = prev[0]
        currentDocs= []
        counter = 0;
        for line in infile:
            userLine = line.split(" ")
            userLineId = userLine[0]
            if (userLineId != prevUser):
                counter += 1
                #print "Number: " + str(counter) + " " + str(len(currentDocs))
                if (len(currentDocs) > 200):
                    currentDocs= []
                    prevDoc = userLine[1]
                    prevUser = userLineId
                    continue;
                currentDocs.append(prevDoc)
                outerCount= 0
                innerCount = 0
                while(outerCount < len(currentDocs)):
                    i = currentDocs[outerCount]
                    while(innerCount < len(currentDocs)):
                        j = currentDocs[innerCount]
                        if (i != j):
                            pairFile.write(str(i) + " " + str(j) + "\n")
                        innerCount += 1
                    innerCount= 0
                    outerCount += 1
                currentDocs= []
            else:
                currentDocs.append(prevDoc)
            prevDoc = userLine[1]
            prevUser = userLineId
    pairFile.close()
    sorter(year)

# Sorts pairs into k1 k2 order
def sorter(year):
    strToPairReduce = str(year) + "_phase4.txt"
    strToPairSort = str(year) + "_phase5.txt"
    os.system('sort -k1,1 -k2,2 ' + strToPairReduce + ' > ' + strToPairSort)
    reducer(year)


# Reduces list of pairs into number of times each one occurs and writes to file
def reducer(year):
    strToPair = str(year) + "_phase5.txt"
    strToPairReduce = str(year) + "_phase6.txt"
    pairReduce= open(strToPairReduce,'wb')
    with open(strToPair,'r+') as infile:
        prev= infile.readline()
        counter= 1
        for line in infile:
            if (line == prev):
                counter += 1
            else:
                pairReduce.write(prev.strip() + " " + str(counter) + "\n")
                prev= line
                counter= 1
        pairReduce.write(prev.strip() + " " + str(counter) + "\n")
    pairReduce.close()
    sorter2(year);


def sorter2(year):
    strToPairReduce = str(year) + "_phase6.txt"
    strToPairSort = str(year) + "_phase7.txt"
    print "Sorting article pair list in " + strToPairReduce + " into " + strToPairSort
    os.system('sort -k1,1 -k3,3r ' + strToPairReduce + ' > ' + strToPairSort)
    print "Done sorting"
    splitter(year)
    print "Done splitting"


#-----------------------------------------------------------------------------------------
# Splits the sorted master pair file into article-specific files in
# the year-specific directory. The names of these files will be the
# same as the respective article IDs, except that slashes are replaced
# with the '@' character
# -------------------------------------------------------------------------------------------
def splitter(year):
    pairReduce = str(year) + '_phase7.txt'
    print "Splitting " + pairReduce + " into article-specific list files in directory " + str(year)
    os.system('mkdir '+ str(year));
#    os.system('chmod 777 *');
    cutOff = 100
    with open(pairReduce, 'r+') as infile:
	os.chdir('' + str(year));
	prev = infile.readline()
	counter = 1
        strHolder= ''
	fileInit = prev.split(" ")
	fileInit = fileInit[0];
	fileInit = fileInit.replace("/","");
	curFile = open(fileInit, 'wb')
	for line in infile:
	    lineS = line.strip().split()
            prevS= prev.strip().split()
	    if (counter < cutOff):
       	        if(lineS[0] == prevS[0]):
		    curFile.write(prevS[1] + " " + prevS[2] + "\n")
	  	    counter += 1
	    else:
		if (lineS[0] != prevS[0]): 
		    curFile.close()
		    fileName = lineS[0];
		    fileName = fileName.replace("/","@");
		    curFile = open(fileName, 'wb')
		    counter = 0
	    prev = line;	    
    os.chdir(outdir);	
				


# merges multiple years into one year
def merger():
    #os.system('sort -m *_phase6.txt > allyears_phaseTemp.txt')
    strToTempMerge = open('tempmerge.txt','wb')
    with open('allyears_phaseTemp.txt','r+') as infile:
        prev= infile.readline()
        prevStrip = prev.strip()
        prevSplit = prevStrip.split(" ")
        counter = int(prevSplit[2])
        for line in infile:
            if (line == prev):
		lineStrip = line.strip()
                lineSplit = lineStrip.split(" ")
                trToTempMerge.write(prev.strip() + " " + str(counter) + "\n")
                counter += int(lineSplit[2])
            else:
		prevSplit = prev.strip().split(" ")
                strToTempMerge.write(prevSplit[0] + " " + prevSplit[1] + " " + str(counter) + "\n")
                prev= line
		lineStrip = line.strip()
                lineSplit = lineStrip.split(" ")
                counter= int(lineSplit[2])
        prevSplit = prev.strip().split(" ")
        strToTempMerge.write(prevSplit[0] + " " + prevSplit[1] + " " + str(counter) + "\n")
    strToTempMerge.close()



# Sorts pairs into k1 k2 order
def findTopTen():
    strToPairReduce = "tempmerge.txt"
    strToPairSort = "findTopTen.txt"
    os.system('sort -T /data/coaccessyears -k1,1 -k3,3r ' + strToPairReduce + ' > ' + strToPairSort)
    



# Maps unique numbers to document id
def inverter():
    topTenHash = dict()
    with open('findTopTen.txt') as inner:
	prev = inner.readline()
	#print prev
        key = prev.strip().split(" ")
	key = key[0]
	counter = 0
	tempList = []
	topK= 10
	for line in inner:
	    if (counter < topK):
		#print line
		lineParser = line.strip().split(" ")
		lineDoc = lineParser[0]
		if (key == lineDoc):	
	            tempList.append(lineParser[1])
		else:
		    topTenHash[key] = tempList;        
		    tempList = [lineParser[1]]
		    key = lineDoc
		    counter = 0
	    else:
		lineParser= line.strip().split(" ")
		lineDoc = lineParser[0]
		if (key != lineDoc):
		     counter = 0
		     key = lineDoc
		     tempList = [lineParser[1]]
	    counter += 1
	    
    with open('merge_top_10_hash.json', 'w') as g:
        g.write(json.dumps(topTenHash))


# Returns top k documents given a document id
def returnK(docId):
    json_data = open('merge_top_10_hash.json')
    topDocHash = json.load(json_data)
    json_data.close()
    return topDocHash[docId]

produceUserList()

'''
#Caching of Hashtables
print("Cache first phase....")
with open('2013_total_doc.json', 'w') as f:
    f.write(json.dumps(total_doc))
on_data = open('2013_top_10_hash.json')
    topDocHash = json.load(json_data)
    json_data.close()
with open('2013_hash_access.json', 'w') as f:
    f.write(json.dumps(hash_access, cls=SetEncoder))
print("First phase done.")

with open('2013_inverse_total_doc.json', 'w') as f:
    f.write(json.dumps(inverse_total_doc))


# Loading total_doc hashtable
json_data= open('2013_total_doc.json')
total_doc= json.load(json_data)
json_data.close()
print "Finished loading total_doc hashtable"
# Loading hashtable of user acceses
json_hdata= open('2013_hash_access.json')
hash_access= json.load(json_hdata)
json_hdata.close()
print "Finished loading hash_access hashtable"

pairGeneration(total_doc, hash_access)


sorter()

reducer()


inverter(10)
'''


