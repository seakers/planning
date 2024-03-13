# create colorblind friendly animation of observations made by satellites of the Earth over time

import time, calendar, datetime
from mpl_toolkits.basemap import Basemap
import matplotlib.pyplot as plt
import matplotlib.animation
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
        if((points[i][2]) < curr_time):
            lats.append((points[i][0])*180/np.pi)
            lons.append((points[i][1])*180/np.pi)
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
                # if(i == 0): # uncomment if file has a header
                #     i = 1
                #     continue
                all_observations.append([row[0],float(row[1]),float(row[2]),float(row[3])])
    observations = []
    satellite_obs = []
    for obs in all_observations:
            observations.append(obs[1:])

filenames = []   
map = Basemap(projection='merc',llcrnrlat=-60,urcrnrlat=80,\
        llcrnrlon=-180,urcrnrlon=180,resolution='c')

map.drawmapboundary(fill_color='paleturquoise')
map.fillcontinents(color='w',lake_color='paleturquoise')

x = []
y = []

# gather observations to animate
obs_lats, obs_lons = get_past_points(observations,86400)
obs_x, obs_y = map(obs_lons,obs_lats); print(len(obs_x))
x.extend(obs_x)
y.extend(obs_y)

# set color blind friendly colors for observations
plt.style.use('tableau-colorblind10') 
colorList = ["C0","C1","C2","C3","C4", "C5","C6","C7","C8","C9"]
colorset = [colorList[0]]
for obs in all_observations:
    if(obs[0]==satellites[0]):
        colorset.append(colorList[0])
    elif(obs[0]==satellites[1]):
        colorset.append(colorList[1])
    elif(obs[0]==satellites[2]):
        colorset.append(colorList[2])
    elif(obs[0]==satellites[3]):
        colorset.append(colorList[3])
    elif(obs[0]==satellites[4]):
        colorset.append(colorList[4])
    elif(obs[0]==satellites[5]):
        colorset.append(colorList[5])
    elif(obs[0]==satellites[6]):
        colorset.append(colorList[6])
    elif(obs[0]==satellites[7]):
        colorset.append(colorList[7])
    elif(obs[0]==satellites[8]):
        colorset.append(colorList[8])
    elif(obs[0]==satellites[9]):
        colorset.append(colorList[9])

del colorset[-1]        # fixes color problem...

# check for correct length
print(len(colorset))
print(len(x))
print(len(y))

# init
x_vals = []
y_vals = []
intensity = []
iterations = len(colorset)

t_vals = np.linspace(0,iterations-1,iterations,dtype=int)
scatter = map.scatter(x_vals, y_vals, s=10)

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

def update(t):
    global x, y, x_vals, y_vals
    x_vals.extend([x[t]])
    y_vals.extend([y[t]])
    scatter.set_offsets(np.c_[x_vals,y_vals])
    scatter.set_color(colorset)

    return ani

plt.legend(handles=[patch0, patch1, patch2, patch3, patch4, patch5, patch6, patch7, patch8, patch9], fontsize=7, bbox_to_anchor =(1, 1.15), ncol = 5)

# animate and save observations as a gif
ani = matplotlib.animation.FuncAnimation(plt.gcf(), update, frames=t_vals, interval=10)
ani.save("C:/path/to/save/file.gif")
