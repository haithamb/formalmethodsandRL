package aub.edu.lb.bip.rl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.encog.engine.network.activation.ActivationFunction;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;

import aub.edu.lb.bip.api.Helper;
import aub.edu.lb.encog.helper.EncogHelper;
import aub.edu.lb.model.BIPInteraction;
import aub.edu.lb.model.Compound;
import aub.edu.lb.model.GlobalState;

public class DeepReinforcementLearning {
	PrintStream ps ; 
	private Compound compound;
	private BasicNetwork networkCurrent; // teta
	private BasicNetwork networkHistory; // teta minus
	private List<TransitionReplay> memoryReplay;
	private Set<String> badStates;

	// configuration
	private int capacityReplay = DefaultSettings.defaultCapacityReplay;
	private int numberEpisodes = DefaultSettings.defaultNumberEpisodes;
	private double probabilityRandom = DefaultSettings.defaultProbabilityRandom;
	private double numberResetHistoryTime = DefaultSettings.defaultResetHistoryTime;
	private int sampleCapacityPercentage  = DefaultSettings.defaultSampleCapacityPercentage;

	private int traceLengthIteration;
	private int numberOfNeuronsHidden = DefaultSettings.defaultNumberOfNeuronsHidden;
	private ActivationFunction activationFunction = DefaultSettings.defaultActivationFunction;
	private ActivationFunction activationFunctionOuter = DefaultSettings.defaultActivationFunctionOuter;

	private double badReward = DefaultSettings.badReward;
	private double goodReward = DefaultSettings.goodReward;
	private double gamma = DefaultSettings.gamma;
	private int epoch = DefaultSettings.EPOCH; 

	private boolean debug = true; 
	
	double[][][] weights;
	double[] bias;
	
	public DeepReinforcementLearning(Compound compound, String fileBadStates) {
		try {
			if(debug) ps = new PrintStream(new File("bench/outputHaitham.txt"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.compound = compound;
		this.badStates = new HashSet<String>();
		fillBadStates(fileBadStates);
		initializeTraceLengthIteration();
		initializeNeuralNetworks();
		memoryReplay = new LinkedList<TransitionReplay>();
		trainEpisodes();
		if(debug) ps.close();
	}

	private void trainEpisodes() {
		for (int i = 1; i <= numberEpisodes; i++) {
			System.out.println("episode " + i);
			trainEpisode();
		}
		setWeights();
		// printWeights(networkCurrent);
	}

	private void trainEpisode() {
		GlobalState currentState = compound.getInitialState();
		
		for (int t = 1; t <= traceLengthIteration; t++) {
			/* select interaction */
			BIPInteraction interaction = pickInteraction(currentState);
			
			/* if deadlock state break the trace and move to the next episode */
			if(interaction == null) break;

			/* execute interaction */
			GlobalState nextState = compound.next(currentState, interaction);

			/*
			 * store in memoryReplay the transition replay 
			 * <currentState, interaction, reward nextState, nextState>
			 */
			boolean isBadStateNext = badStates.contains(nextState.toString());
			double reward = isBadStateNext ? badReward : goodReward;
			TransitionReplay transition = new TransitionReplay(currentState, interaction, nextState, reward);
			
			memoryReplay.add(transition);
			if(memoryReplay.size() > capacityReplay) memoryReplay.remove(0);
			
			train();
		
			/* every c steps reset teta minus */
			if((t % numberResetHistoryTime) == 0) {
				EncogHelper.copyWeights(networkCurrent, networkHistory);
			}
			
			/* if next state is bad break and move to the next episode */
			if(isBadStateNext) break;
			
			currentState = nextState; 
		}
	}
	
	private void train() {
		List<TransitionReplay> miniBatch = sampleMiniBatch();
		train(miniBatch);
	}
	
	private void train(List<TransitionReplay> miniBatch) {
		double[][] input = new double[miniBatch.size()][];
		double[][] output = new double[miniBatch.size()][];
		int i = 0;
		if(debug) ps.println("start batch size of " + miniBatch.size());
		for(TransitionReplay t: miniBatch) {
			output[i] = generateTrainingPoint(t);
			input[i] = t.getFromState().getIds();
			if(debug) {
				ps.println(t);
				ps.println(Arrays.toString(input[i]));
				ps.println(Arrays.toString(output[i]) + "\n");
			}	
			i++;
		}
		if(debug) ps.println("end batch\n\n\n");
		EncogHelper.learning(networkCurrent, input, output, epoch, DefaultSettings.EPS);
	}
	
	private double[] generateTrainingPoint(TransitionReplay transition) {
		boolean isBadState = badStates.contains(transition.getToState().toString()); 
		double[] outputNetworkCurrentState = EncogHelper.forwardPropagation(networkHistory, transition.getFromState().getIds());
		List<BIPInteraction> enabledInteractions = compound.getEnabledInteractions(transition.getToState());

		if(isBadState || enabledInteractions == null || enabledInteractions.size() == 0) {
			outputNetworkCurrentState[transition.getInteraction().getId()] = transition.getReward();
		} else {	
			double[] outputNetworkNextState = EncogHelper.forwardPropagation(networkHistory, transition.getToState().getIds());
			double maxOutput = -Double.POSITIVE_INFINITY;
			for (BIPInteraction interaction : enabledInteractions) {
				int id = interaction.getId();
				if (outputNetworkNextState[id] > maxOutput) {
					maxOutput = outputNetworkNextState[id];
				}
			}
			outputNetworkCurrentState[transition.getInteraction().getId()] = transition.getReward() + gamma * maxOutput;
		}
		return outputNetworkCurrentState;
	}
	
	private List<TransitionReplay> sampleMiniBatch() {
		List<TransitionReplay> miniBatch = new ArrayList<TransitionReplay>();
		int sizeMiniBatch = (int) Math.ceil((1.0 * memoryReplay.size() * sampleCapacityPercentage) / 100); 
		int[] miniBatchIndices = Helper.generateRandomIndices(sizeMiniBatch, memoryReplay.size());
		for(int i = 0; i < miniBatchIndices.length; i++) {
			miniBatch.add(memoryReplay.get(miniBatchIndices[i]));
		}
		return miniBatch;
		
	}

	private void fillBadStates(String fileBadStates) {
		try {
			Scanner in = new Scanner(new File(fileBadStates));
			while (in.hasNextLine()) {
				String badState = in.nextLine();
				badStates.add(badState);
			}
			in.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param state
	 * @return pick random interaction with probability @probabilityRandom pick
	 *         interaction w.r.t. exploitation, i.e., a_select = argmax(Q(s,a;
	 *         teta)) where a in enable interaction. Return null if the input
	 *         state is deadlock state
	 */
	private BIPInteraction pickInteraction(GlobalState state) {
		List<BIPInteraction> enabledInteractions = compound.getEnabledInteractions(state);
		if (enabledInteractions == null || enabledInteractions.size() == 0)
			return null; // deadlock state
		if (!isExploit()) { // random Selection
			int indexInteraction = Helper.random(0, enabledInteractions.size());
			return enabledInteractions.get(indexInteraction);
		} else {
			double[] outputNetwork = EncogHelper.forwardPropagation(networkCurrent, state.getIds());
			BIPInteraction maxInteraction = null;
			double maxOutput = -Double.POSITIVE_INFINITY;
			for (BIPInteraction interaction : enabledInteractions) {
				int id = interaction.getId();
				if (outputNetwork[id] >= maxOutput) {
					maxInteraction = interaction;
					maxOutput = outputNetwork[id];
				}
			}
			return maxInteraction;
		}
	}
	
	
	public double[] getOutput(double[] state) {
		return EncogHelper.forwardPropagation(networkCurrent, state);
	}
	
	
	private boolean isExploit() {
		return Math.random() > probabilityRandom;
	}

	/**
	 * 
	 */
	private void initializeNeuralNetworks() {
		networkCurrent = new BasicNetwork();
		networkCurrent.addLayer(new BasicLayer(null, true, compound.stateLength()));
		networkCurrent.addLayer(new BasicLayer(activationFunction, true, numberOfNeuronsHidden));
		networkCurrent.addLayer(new BasicLayer(activationFunctionOuter, false, compound.getInteractions().size()));
		networkCurrent.getStructure().finalizeStructure();
		networkCurrent.reset();
		networkHistory = (BasicNetwork) networkCurrent.clone();
	}

	public Compound getCompound() {
		return compound;
	}
	
	public int getInputNetworkSize() {
		return compound.stateLength() + 1; // one for bias
	}
	
	public int getHiddenLayerNetworkSize() {
		return numberOfNeuronsHidden + 1; // one for bias
	}
	
	public int getOutputNetworkSize() {
		return compound.getInteractions().size();
	}
	

	/**
	 * TODO: Tweak it w.r.t syntax Diameter or number of locations
	 */
	public void initializeTraceLengthIteration() {
		traceLengthIteration = DefaultSettings.defaultTraceLengthIteration;
	}
	
	public void setWeights() {
		weights = new double[networkCurrent.getLayerCount() - 1][][];
		bias = new double[networkCurrent.getLayerCount() - 1];

		int numberLayers = networkCurrent.getLayerCount();
		for (int i = 0; i < numberLayers - 1; i++) {
			weights[i] = new double[networkCurrent.getLayerTotalNeuronCount(i)][networkCurrent.getLayerTotalNeuronCount(i + 1)];
			bias[i] = networkCurrent.getLayerBiasActivation(i);
			// from layer getLayerTotalNeuronCount
			for (int neuronFrom = 0; neuronFrom < networkCurrent.getLayerTotalNeuronCount(i); neuronFrom++) {
				// to layer getLayerNeuronCount (from is not connected to the
				// bias neuron and,
				// the bias weight is the last weight on each layer
				for (int neuronTo = 0; neuronTo < networkCurrent.getLayerNeuronCount(i + 1); neuronTo++) {
					weights[i][neuronFrom][neuronTo] = networkCurrent.getWeight(i, neuronFrom, neuronTo);
				}
			}
		}
	}
	
	public void printWeights(BasicNetwork fromNetwork) {
		int numberLayers = fromNetwork.getLayerCount();
		for (int i = 0; i < numberLayers - 1; i++) {
			System.out.println("from layer " + i + " to layer " + (i + 1));
			System.out.println("layer bias = " + fromNetwork.getLayerBiasActivation(i));
			// from layer getLayerTotalNeuronCount
			for (int neuronFrom = 0; neuronFrom < fromNetwork.getLayerTotalNeuronCount(i); neuronFrom++) {
				// to layer getLayerNeuronCount (from is not connected to the
				// bias neuron and,
				// the bias weight is the last weight on each layer
				for (int neuronTo = 0; neuronTo < fromNetwork.getLayerNeuronCount(i + 1); neuronTo++) {
					System.out.println("from " + neuronFrom + " -- to " + neuronTo + " --> "
							+ fromNetwork.getWeight(i, neuronFrom, neuronTo));
				}
			}
			System.out.println("-------------------------------------");
		}
	}

	
	public double[][][] getWeights() {
		return weights; 
	}
	
	public double[] getBias() {
		return bias; 
	}
}
