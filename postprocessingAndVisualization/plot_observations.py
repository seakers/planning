# create colorblind friendly figure of observations made by satellites of the Earth

import time, calendar, datetime
from mpl_toolkits.basemap import Basemap
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import urllib, os
import csv
import numpy as np
import imageio
import sys

def unique(lakes):
    lakes = np.asarray(lakes)[:,0:1]
    return np.unique(lakes,axis=0)

def time_unique(lakes):
    lakes = np.asarray(lakes)
    return np.unique(lakes,axis=0)

def get_past_points(points, curr_time):
    lats = []
    lons = []
    i = 0
    while i < len(points):
        if(float(points[i][2]) < curr_time):
            lats.append(float(points[i][0])*180/np.pi)
            lons.append(float(points[i][1])*180/np.pi)
        else:
            break
        i = i+1
    return lats, lons

def get_curr_points(points, curr_time):
    lats = []
    lons = []
    i = 0
    while i < len(points):
        if(float(points[i][2]) < curr_time < float(points[i][3])):
            lats.append(float(points[i][0])*180/np.pi)
            lons.append(float(points[i][1])*180/np.pi)
        i = i+1
    return lats, lons

def get_ground_track(points, curr_time, window):
    lats = []
    lons = []
    i = 0
    while i < len(points):
        if((curr_time - window) < float(points[i][2]) < curr_time):
            lats.append(float(points[i][0]))
            lons.append(float(points[i][1]))
        i = i+1
    return lats, lons

if __name__=="__main__":
    # assign directory
    directory = sys.argv[1]
    satellites = ["smallsat00","smallsat01","smallsat02","smallsat03","smallsat04", "smallsat05","smallsat06","smallsat07","smallsat08","smallsat09"]
    observations_by_sat = {}
    # iterate over files in
    # that directory
    all_observations = []
    for filename in os.listdir(directory):
        f = os.path.join(directory, filename)
        # checking if it is a file
        if os.path.isfile(f):
            with open(f) as csv_file:
                csv_reader = csv.reader(csv_file, delimiter=',')
                i = 0
                for row in csv_reader:
                    # if(i == 0):
                    #     i = 1
                    #     continue # uncomment if there are headers
                    all_observations.append([row[0],float(row[1]),float(row[2]),float(row[3])])
    for sat in satellites:
        observations = []
        for obs in all_observations:
            if(obs[0]==sat):
                observations.append(obs[1:])
        observations_by_sat[sat] = observations

    filenames = []   
    m = Basemap(projection='merc',llcrnrlat=-60,urcrnrlat=80,\
            llcrnrlon=-180,urcrnrlon=180,resolution='c')
    
    m.drawmapboundary(fill_color='paleturquoise')
    m.fillcontinents(color='w',lake_color='paleturquoise')
    i = 0
    # set color blind friendly colors for observations
    plt.style.use('tableau-colorblind10') 
    colorList = ["C0","C1","C2","C3","C4", "C5","C6","C7","C8","C9"]
    for sat in satellites:
        obs_lats, obs_lons = get_past_points(observations_by_sat[sat],86400); print(len(obs_lats))
        obs_x, obs_y = m(obs_lons,obs_lats)
        m.scatter(obs_x,obs_y,len(satellites)-i,marker='o',color=colorList[i], label = sat)
        i = i+1
    ax = plt.gca()
    box = ax.get_position()
    ax.set_position([box.x0, box.y0, box.width, box.height])

    # create colorblind friendly legend 
    patch0 = mpatches.Patch(color=colorList[0], label=satellites[0])
    patch1 = mpatches.Patch(color=colorList[1], label=satellites[1])
    patch2 = mpatches.Patch(color=colorList[2], label=satellites[2])
    patch3 = mpatches.Patch(color=colorList[3], label=satellites[3])
    patch4 = mpatches.Patch(color=colorList[4], label=satellites[4])
    patch5 = mpatches.Patch(color=colorList[5], label=satellites[5])
    patch6 = mpatches.Patch(color=colorList[6], label=satellites[6])
    patch7 = mpatches.Patch(color=colorList[7], label=satellites[7])
    patch8 = mpatches.Patch(color=colorList[8], label=satellites[8])
    patch9 = mpatches.Patch(color=colorList[9], label=satellites[9])

    plt.legend(handles=[patch0, patch1, patch2, patch3, patch4], fontsize=7, loc='upper center', bbox_to_anchor=(0.5, 1.1), ncol = 5)
    plt.savefig("C:/path/to/save/file.png",dpi=300)
    plt.close()
    print('Charts saved\n')