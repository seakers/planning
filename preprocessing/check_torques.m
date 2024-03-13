%% check_torques.m
% Use with process_accesses.m
% Ensure solution file from SCIP does not violate slewing constraint
% o#t#p#s val nothing
sol = readtable('.\path\to\scip\sol.txt','Delimiter',{' ','#'},'NumHeaderLines',2);
sat_data = sol(sol.Var4 == 0,:);            % get o#t#p#s table data
torque_max = 0.1;                           % Nm
slew_rate_max = 4;                          % deg/s
I = 2.66;                                   % inertia, kg-m2
for ti = 1:height(sat_data)-1
    s1 = sol.Var4(ti);                      % get s1
    s2 = sol.Var4(ti + 1);                  % get s2
    t1 = TIndexes2.get(sol.Var2(ti));       % get k = t-1
    t2 = TIndexes2.get(sol.Var2(ti + 1));   % get t
    dt = (t2 - t1);
    p1 = sol.Var3(ti);                      % get p
    theta1 = 5*(p1 - 7);                    % convert theta1 from int to deg
    p2 = sol.Var3(ti + 1);                  % get p'
    theta2 = 5*(p2 - 7);                    % convert theta2 from int to deg
    dtheta = abs(deg2rad(theta2) - deg2rad(theta1));
    % TORQUE ===================
    torque = 4*I*dtheta/(dt^2);
    if (torque > torque_max) && (s1 == s2)
    % ANGULAR VELOCITY =========
    %angular_velocity = dtheta/dt;
    %if angular_velocity > slew_rate_max
        fprintf('Slewing constraint violated s1 = %f, s2 = %f, t1 = %f, t2 = %f, p1 = %d, p2 = %d, dtheta = %d, dt = %f, rate = %f\n',s1,s2,sol.Var2(ti),sol.Var2(ti+1),p1,p2,dtheta,dt,torque);
    end
end