# parameters are taken from simulation csv
param Smax := 4; # maximum number of satellites
param Pmax := 13; # total number of pointing options
param Imax := 403; # total number of images
param Tmax := 622; # simulation time window in seconds
param inertia := 2.66; # intertia, kg*m^2
param max_torque := 0.1; # 0.1 Nm (kg-m2/s2)
param max_slew_time := sqrt(4*inertia*(3.14159/3)/max_torque);

# inclusive ranges
set S := {0..Smax}; 	# set of satellites
set P := {7..Pmax}; 	# set of pointing options
set I := {1..Imax}; 	# set of images
set T := {1..Tmax}; 	# time duration
set K := {1..(Tmax-1)}; 
set X := {1..3};    	# altimeter, visible and TIR imagers     
set TS := T*S;
set T2 := {2..Tmax};
set ST2K := S*T2*K;
set TPS := T*P*S;
set TIPS := T*I*P*S;
set TIPSX := T*I*P*S*X;
set SX := S*X;
set IX := I*X;
set ITX := I*T*X;
set IT2X := I*T2*X;
set ITKX := I*T*K*X;
set SPK := S*P*K;
set SP := S*P;

# set accesses (pre-computed A-matrix)
param access[TIPS] :=
	read "C:\path\to\accesses_1h_5sat_processed.csv" as "<11n,10n,9n,8n> 12n" skip 1 default 0;

# slewing constraint parameters
param times[T] := 
	read "C:\path\to\accesses_1h_5sat_processed.csv" as "<11n> 5n" skip 1 default 0;

# sensor types
param sensor_type[SX] := 
	read "C:\path\to\sensor_type_5sat.csv" as "<1n,2n> 3n" skip 1 default 0;

# set initial reward values
param r[I] := read "C:\path\to\accesses_1h_5sat_processed.csv" as "<10n> 7n" skip 1 default 0;

# approximate pointing angles
param theta[<p> in P] := 5*(p-7);

# variables used in the model
var o[<t,p,s> in TPS] binary; 		# binary observation variable

do print "done";


# objective: maximize rewards
maximize obj: sum <t,i,p,s,x> in TIPSX : (o[t,p,s] * access[t,i,p,s] * r[i]*sensor_type[s,x]);

# one pointing direction at a time
subto point : forall <t,s> in TS do
	sum <p> in P: o[t,p,s] <= 1;

# remove impossible observations (due to precomputed matrices)
subto remove_null_accesses: forall <t,p,s> in TPS do
	if sum <i> in I : access[t,i,p,s]==0 then o[t,p,s]==0 end;

# slew torque constraint
subto slew_torque: forall <s,t,k> in ST2K with k<t and (times[t] - times[k] <= max_slew_time) do 
	vif sum <p> in P : o[t,p,s] == 1 and sum <p> in P : o[k,p,s] == 1 then
		(4*inertia*abs((sum <p> in P : o[t,p,s]*theta[p]) - (sum <p> in P : o[k,p,s]*theta[p]))/((times[t] - times[k])^2)) <= max_torque end, indicator;

# reward constraint
subto reward: forall <i,x> in IX do
	sum <t,p,s> in TPS : o[t,p,s] * access[t,i,p,s] * sensor_type[s,x] <= 1;




