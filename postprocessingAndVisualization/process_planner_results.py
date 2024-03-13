# postprocess solution files from the planners using the updated reward function of choice 

import csv
import numpy as np
import decimal
import re
import os

def close_enough(lat0,lon0,lat1,lon1):
    if decimal.Decimal(np.sqrt((lat0-lat1)**2+(lon0-lon1)**2)) < decimal.Decimal(0.0001*np.pi/180):
        return True
    else:
        return False
    
def get_reward(lat,lon,angle,rewards):
    reward = 0
    for rew in rewards:
        if close_enough(lat,lon,decimal.Decimal(rew[0]),decimal.Decimal(rew[1])):
            reward += decimal.Decimal(rew[2])
    reward *= (1-angle)
    return reward

def sensor_type(sat_num):
    if sat_num == 0 or sat_num == 3 or sat_num == 6 or sat_num == 9:
        return 1
    elif sat_num == 1 or sat_num == 4 or sat_num == 7:
        return 2
    elif sat_num == 2 or sat_num == 5 or sat_num == 8:
        return 3

def update_rewards(observations):
    curr_time = 0
    total_reward = 0
    for obs in observations:
        if decimal.Decimal(obs[3]) < curr_time:
            print("Observations are not sorted.")
        curr_time = decimal.Decimal(obs[3])
        already_observed = False
        if len(already_observed_locations) > 0:
            for already_obs in already_observed_locations:
                if close_enough(decimal.Decimal(obs[1]),decimal.Decimal(obs[2]),already_obs[0],already_obs[1]):
                    already_observed = True
                    break
        sat_num = int(re.findall(r'\d{1,100}',obs[0])[0])
        already_observed_locations.append((decimal.Decimal(obs[1]), decimal.Decimal(obs[2]), sensor_type(sat_num)))
        if already_observed and reobservations_penalized:
            # reward = 0 # no reobservation reward
            reward = get_reward(decimal.Decimal(obs[1]),decimal.Decimal(obs[2]),decimal.Decimal(obs[5]),rewards)*decimal.Decimal(np.exp(-already_observed_locations.count((decimal.Decimal(obs[1]), decimal.Decimal(obs[2]), sensor_type(sat_num)))))  # exponentially decaying reward
            # reward = get_reward(decimal.Decimal(obs[1]),decimal.Decimal(obs[2]),decimal.Decimal(obs[5]),rewards)*(1-already_observed_locations.count((decimal.Decimal(obs[1]), decimal.Decimal(obs[2]), sensor_type(sat_num)))*decimal.Decimal(0.1)) # linearly decaying reward
        else:
            reward = get_reward(decimal.Decimal(obs[1]),decimal.Decimal(obs[2]),decimal.Decimal(obs[5]),rewards)
        total_reward += reward

    print("\tTotal reward: "+str(total_reward))


rewards = []
# rewards output from xplanner
# file is formatted [lat,lon,reward]
with open('C:/path/to/directory/rewards.csv',newline='') as csv_file: 
    spamreader = csv.reader(csv_file, delimiter=',', quotechar='|')
    i = 0
    for row in spamreader:
        if i < 1:
            i=i+1
            continue
        rewards.append(row)

# assume that observations are sorted
# file is formatted [sat,lat,lon,start_time,end_time,incidence_angle,reward], all in radians
directory = os.path.join("C:/","path/to/directory/")
for root,dirs,files in os.walk(directory):
    for file in files:
        if file.endswith(".csv"):
            with open(os.path.join(directory,file),newline='') as csv_file:
                spamreader = csv.reader(csv_file, delimiter=',', quotechar='|')
                i = 0
                observations = []
                for row in spamreader:
                    if i < 1:
                        i=i+1
                        continue # remove if missing headers
                    observations.append(row)
                already_observed_locations = [] 
                print(file)
                reobservations_penalized = True
                update_rewards(observations)
                