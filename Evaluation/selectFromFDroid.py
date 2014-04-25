#!/bin/usr/python
import random

file = open('FDroidLinks.txt')
f = file.readlines()

output = open('selected.txt', 'w')

for i in random.sample(xrange(1,len(f)), 100): 
	print f[i]
	output.write(f[i]);

file.close()
output.close()