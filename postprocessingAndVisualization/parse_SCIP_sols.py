# parse SCIP solutions from a .sol/.txt file to a .csv file 

import re
import csv

# set t,p,s arrays
t_sol = []
p_sol = []
s_sol = []

tps_sol = []

# use regex to get t, p, s, and reward from an observation
def extract_tps(scip_line):
    tps_array = re.findall("(\d+\.\d+|\d+)",scip_line)
    tps_sol.append([tps_array[0],tps_array[1],tps_array[2],tps_array[4]])

# file name used to process solutions
file_name = "file_name"

# read solution file (.sol/.txt)
sol_file_name = "./path/to/directory/"+file_name+".sol"
def read_solution_file(sol_file_name):
    with open(sol_file_name, "r") as f:
        lines = f.readlines()

    for line in lines:
        if re.search("^o#",line) != None:
            extract_tps(line.strip())

read_solution_file(sol_file_name)

# find matching tps in access csv and use pre-processed access csv to create solution csv file (for post-processing and visualizations)
obs = []
csv_file_name = "./path/to/directory/"+file_name+".csv"
with open(csv_file_name,'r') as file:
    csvFile = csv.reader(file)
    
    for lines in csvFile:
        for tps in tps_sol:
            if tps[:3] == [lines[10], lines[8], lines[7]]:
                obs.append(lines) 
        
print(len(obs))
print(len(tps_sol))


# output sol as csv
gif_file_name = "./path/to/directory/"+file_name+".csv"
with open(gif_file_name,'w', newline='') as csvFile:
    csvWriter = csv.writer(csvFile)
    csvWriter.writerows(obs)
