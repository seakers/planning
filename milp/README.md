To run a MILP model, first ensure that the SCIP Optimization Suite is installed on your device. 
SCIP is open source and can be installed using this link: https://scipopt.org/index.php#download

For ease of use, the Windows executable is recommended.

Once SCIP is installed, you can run a MILP zimpl (.zpl) model by entering the following commands in the SCIP shell:

SCIP> read "\path\to\MILP\model.zpl"
SCIP> optimize
SCIP> display solution

Once a model has been solved, you can also use:
SCIP> write solution "directory\to\save\solution.sol"
SCIP> write statistics "directory\to\save\statistics.sol"

These saved solution files can be used to postprocessing to compare rewards with other planners or create visualizations.