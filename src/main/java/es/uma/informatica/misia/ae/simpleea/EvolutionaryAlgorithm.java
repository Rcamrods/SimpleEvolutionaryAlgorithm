package es.uma.informatica.misia.ae.simpleea;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class EvolutionaryAlgorithm {
	public static final String POPULATION_SIZE_PARAM = "populationSize";
	public static final String MAX_FUNCTION_EVALUATIONS_PARAM = "maxFunctionEvaluations";
	public static final String CROSSOVER_PROBABILITY_PARAM = "crossoverprob";
	public static final String PROBLEM_SIZE_PARAM = "problemsize";
	public static final String EARLYSTOP_PARAM = "earlystop";
	public static final String RANDOM_SEED_PARAM = "randomSeed";

	private int populationSize;
	private int maxFunctionEvaluations;
	private double crossoverprob;
	private double bitFlipProb;
	private int problemsize;
	private Boolean earlystop;
	private long randomSeed;

	private Problem problem;
	private int functionEvaluations;
	private List<Individual> population;

	private Random rnd;
	private Individual bestSolution;

	private Selection selection;
	private Replacement replacement;
	private Mutation mutation;
	private Crossover recombination;

	private FileWriter csvWriter;

	public EvolutionaryAlgorithm(Map<String, Double> parameters, Problem problem) {
		configureAlgorithm(parameters, problem);
		initializeCsvWriter();
	}

	private void initializeCsvWriter() {
		String directoryName = String.format("Data/ProS_%d/Es_%b/BITFLIP_%.2f_CROSSOVER_%.2f/", problemsize, earlystop, bitFlipProb, crossoverprob);
		File directory = new File(directoryName);
		if (!directory.exists()) {
			directory.mkdirs();
		}
		String fileName = String.format("randomSeed_%d.csv", randomSeed);
		File csvFile = new File(directory, fileName);
		try {
			csvWriter = new FileWriter(csvFile);
			csvWriter.append("Evaluations;BestFitness;MeanFitness;Diversity;UniqueIndividuals;PopulationSize;ProblemSize;BitFlipProbability;CrossoverProbability;RandomSeed\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void configureAlgorithm(Map<String, Double> parameters, Problem problem) {
		populationSize = parameters.get(POPULATION_SIZE_PARAM).intValue();
		maxFunctionEvaluations = parameters.get(MAX_FUNCTION_EVALUATIONS_PARAM).intValue();
		bitFlipProb = parameters.get(BitFlipMutation.BIT_FLIP_PROBABILITY_PARAM);
		crossoverprob = parameters.get(CROSSOVER_PROBABILITY_PARAM);
		problemsize = parameters.get(PROBLEM_SIZE_PARAM).intValue();
		earlystop = (parameters.get(EARLYSTOP_PARAM) != 0.0);
		randomSeed = parameters.get(RANDOM_SEED_PARAM).longValue();
		this.problem = problem;
		rnd = new Random(randomSeed);
		selection = new BinaryTournament(rnd);
		replacement = new ElitistReplacement();
		mutation = new BitFlipMutation(rnd, bitFlipProb);
		recombination = new SinglePointCrossover(rnd);
	}

	public Individual run() {
		population = generateInitialPopulation();
		functionEvaluations = 0;
		evaluatePopulation(population);
		while (functionEvaluations < maxFunctionEvaluations) {
			Individual parent1 = selection.selectParent(population);
			Individual parent2 = selection.selectParent(population);
			Individual child;
			if (Math.random() <= crossoverprob) {
				child = recombination.apply(parent1, parent2);
			} else {
				child = parent1;
			}
			child = mutation.apply(child);
			evaluateIndividual(child);
			population = replacement.replacement(population, Arrays.asList(child));
			recordStatistics();
			if (earlystop && child.getFitness() == problemsize) {
				break;
			}
		}
		try {
			csvWriter.flush();
			csvWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bestSolution;
	}

	private void recordStatistics() {
		double meanFitness = calculateMeanFitness();
		double diversity = calculateDiversity();
		int uniqueIndividuals = calculateUniqueIndividuals();
		try {
			csvWriter.append(String.format("%d;%d;%.4f;%.4f;%d;%d;%d;%.2f;%.2f;%d\n",
					functionEvaluations,
					(int) bestSolution.getFitness(),
					meanFitness,
					diversity,
					uniqueIndividuals,
					populationSize,
					problemsize,
					bitFlipProb,
					crossoverprob,
					randomSeed));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private double calculateMeanFitness() {
		double totalFitness = 0.0;
		for (Individual individual : population) {
			totalFitness += individual.getFitness();
		}
		return totalFitness / population.size();
	}

	private double calculateDiversity() {
		double totalDistance = 0.0;
		int comparisons = 0;
		for (int i = 0; i < population.size(); i++) {
			BinaryString individualA = (BinaryString) population.get(i);
			byte[] chromosomeA = individualA.getChromosome();
			for (int j = i + 1; j < population.size(); j++) {
				BinaryString individualB = (BinaryString) population.get(j);
				byte[] chromosomeB = individualB.getChromosome();
				totalDistance += hammingDistance(chromosomeA, chromosomeB);
				comparisons++;
			}
		}
		return comparisons > 0 ? totalDistance / comparisons : 0.0;
	}

	private int calculateUniqueIndividuals() {
		Set<String> uniqueIndividuals = new HashSet<>();
		for (Individual individual : population) {
			BinaryString binaryIndividual = (BinaryString) individual;
			byte[] chromosome = binaryIndividual.getChromosome();
			uniqueIndividuals.add(Arrays.toString(chromosome));
		}
		return uniqueIndividuals.size();
	}

	private int hammingDistance(byte[] a, byte[] b) {
		int dist = 0;
		int len = a.length;
		int i = 0;
		int limit = len - (len % 4);
		for (; i < limit; i += 4) {
			if (a[i] != b[i]) dist++;
			if (a[i + 1] != b[i + 1]) dist++;
			if (a[i + 2] != b[i + 2]) dist++;
			if (a[i + 3] != b[i + 3]) dist++;
		}
		for (; i < len; i++) {
			if (a[i] != b[i]) {
				dist++;
			}
		}
		return dist;
	}

	private void evaluateIndividual(Individual individual) {
		double fitness = problem.evaluate(individual);
		individual.setFitness(fitness);
		functionEvaluations++;
		checkIfBest(individual);
	}

	private void checkIfBest(Individual individual) {
		if (bestSolution == null || individual.getFitness() > bestSolution.getFitness()) {
			bestSolution = individual;
		}
	}

	private void evaluatePopulation(List<Individual> population) {
		for (Individual individual : population) {
			evaluateIndividual(individual);
		}
	}

	private List<Individual> generateInitialPopulation() {
		List<Individual> population = new ArrayList<>();
		for (int i = 0; i < populationSize; i++) {
			population.add(problem.generateRandomIndividual(rnd));
		}
		return population;
	}
}