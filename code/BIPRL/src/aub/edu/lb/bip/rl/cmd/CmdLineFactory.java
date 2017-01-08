package aub.edu.lb.bip.rl.cmd;


import jcmdline.CmdLineHandler;
import jcmdline.FileParam;
import jcmdline.HelpCmdLineHandler;
import jcmdline.IntParam;
import jcmdline.Parameter;
import jcmdline.StringParam;
import jcmdline.VersionCmdLineHandler;



public class CmdLineFactory {
	private static final String defaultInputBIP = "input.bip";
	private static final String helpInputBIPFile = "input BIP file name";
	private static final String cmdLineTool = "java -jar BIPRL.jar";
	
	private static final String defaultOutputFile = "input.bip";
	private static final String helpOutputFile = "output file to be automatically generated";
	public static final String doubleErrorMessage = "good reward, badReward, probality random and gamma must be double";
	public static final String infiniteAllOrNothing = "In case of infinite option, you either set all the parameters or none (default values will be set)";
	private static final String VERSION = "V 1.0";
	
	private static final String cmdLineDescription = "A tool to enforce properties using reinforcement learning!";
	
	private static final String helpText = "Have Fun!";
		
	
	public static final String normal = "normal";
	public static final String finite = "value-iterator";
	public static final String infinite = "infinite";

	public static final String typeNotSupportedError = "Mode is not supported (" +
			normal + ", " + finite + ", or " + infinite;

	public StringParam mode;
	public FileParam inputBIP; 
	public FileParam outputFile; 
	public FileParam badStates; 
	
	public static final String ErrorGeneratingFile = "Error while generating source code";

	public StringParam goodReward, badReward, probaRandom, gamma;
	public IntParam episodes, epoch, hidden, capacity, minimumTrace, fair;
	public IntParam sampleCapacityPercentage, resetHistoryPeriod;
	public CmdLineHandler cmdLineHandler;
	
	public IntParam maxIterationValueIterator; 

	
	public CmdLineFactory(String[] args) {
		inputBIP = new FileParam(defaultInputBIP, helpInputBIPFile,
				FileParam.EXISTS & FileParam.IS_READABLE,
				!FileParam.OPTIONAL,
				!FileParam.MULTI_VALUED
			);
		
		mode = new StringParam("mode", "\n" + finite + ": for finite case.\n" + 
		infinite + ": for infinite case.\n" + 
		normal + ": without reinforcement learning");
		
		outputFile = new FileParam(defaultOutputFile, helpOutputFile,
				FileParam.EXISTS & FileParam.IS_READABLE,
				!FileParam.OPTIONAL,
				!FileParam.MULTI_VALUED
			);
		
		badStates = new FileParam("badStates", "File containing bad states to be avoided",
				FileParam.EXISTS & FileParam.IS_READABLE,
				!FileParam.OPTIONAL,
				!FileParam.MULTI_VALUED
			);
		
		maxIterationValueIterator = new IntParam("max-iteration", "Bound iteration");

		
		goodReward = new StringParam("good-reward", "Good reward value");
		badReward = new StringParam("bad-reward", "Bad reward value");
		probaRandom = new StringParam("proba-random", "Probability of exploration");
		gamma = new StringParam("gamma", "Gamma");

		episodes = new IntParam("episodes", "Number of episodes");
		epoch = new IntParam("epoch", "Epoch");
		hidden = new IntParam("hidden", "Number of neurons in hidden layer");
		capacity = new IntParam("capacity", "Capacity of memory replay");
		minimumTrace = new IntParam("trace", "Minimum trace lenght (trace length is guaranteed to be greater than minimum trace length and diameter of all atomic components)");
		sampleCapacityPercentage = new IntParam("sample-capacity", "Size of samples used for training");
		resetHistoryPeriod = new IntParam("period-reset", "Reset period time");
		fair = new IntParam("Fairness degree", "Fairness degree");

		cmdLineHandler = new VersionCmdLineHandler(VERSION,
				(CmdLineHandler) new HelpCmdLineHandler(helpText, cmdLineTool, cmdLineDescription,
						new Parameter[] { episodes, goodReward, badReward, epoch, hidden, 
								probaRandom, gamma, capacity, minimumTrace, sampleCapacityPercentage,
								resetHistoryPeriod },
						new Parameter[] { inputBIP, outputFile } ));
		
		cmdLineHandler.parse(args);	
	}
	
	public boolean allSetsInfinite() {
		return 	goodReward.isSet() && badReward.isSet() &&  probaRandom.isSet() && 
				gamma.isSet() && episodes.isSet() && epoch.isSet() &&
				hidden.isSet() && capacity.isSet() && minimumTrace.isSet() &&
				sampleCapacityPercentage.isSet() && resetHistoryPeriod.isSet();
	}
	
	public boolean allNotSets() {
		return 	!goodReward.isSet() && !badReward.isSet() &&  !probaRandom.isSet() && 
				!gamma.isSet() && !episodes.isSet() && !epoch.isSet() &&
				!hidden.isSet() && !capacity.isSet() && !minimumTrace.isSet() &&
				!sampleCapacityPercentage.isSet() && !resetHistoryPeriod.isSet();
	}

	public String getInputBIPFile() {
		return inputBIP.getValue().getAbsolutePath();
	}
	
	public String getOutputFile() {
		return outputFile.getValue().getAbsolutePath();
	}
	
	public String getBadStates() {
		return badStates.getValue().getAbsolutePath();
	}
	
	public boolean validDoubleInfinite() {
		try {
			Double.parseDouble(goodReward.getValue());
			Double.parseDouble(badReward.getValue());
			Double.parseDouble(probaRandom.getValue());
			Double.parseDouble(gamma.getValue());
			return true;
		} catch(Exception e) {
			return false;
		}
	}
 
}