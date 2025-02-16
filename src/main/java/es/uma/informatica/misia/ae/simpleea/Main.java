package es.uma.informatica.misia.ae.simpleea;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class Main {
	public static void main(String args[]) {
		if (args.length != 2) {
			System.err.println("Invalid number of arguments");
			System.err.println("Arguments: <population size> <function evaluations>");
			return;
		}
		String folderPath = "Data";
		File folder = new File(folderPath);
		if (deleteFolder(folder)) {
			System.out.println("The folder was successfully deleted.");
		} else {
			System.out.println("The folder could not be deleted. Please check the path or permissions.");
		}
		int numSeeds = 100;
		long totalTasks = numSeeds * 2 * 6 * 5 * 2;
		ExecutorService executor = Executors.newFixedThreadPool(10);
		ScheduledExecutorService progressMonitor = Executors.newSingleThreadScheduledExecutor();
		final long startTime = System.currentTimeMillis();
		progressMonitor.scheduleAtFixedRate(() -> {
			if (executor instanceof ThreadPoolExecutor) {
				ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
				long completed = tpe.getCompletedTaskCount();
				double progressPercent = 100.0 * completed / totalTasks;
				int progressChars = (int) (progressPercent / 2);
				String progressBar = "#".repeat(progressChars) + " ".repeat(50 - progressChars);
				long elapsedMillis = System.currentTimeMillis() - startTime;
				String elapsedTime = formatTime(elapsedMillis);
				String remainingTime = "Computing...";
				if (completed > 0) {
					double avgTaskTime = (double) elapsedMillis / completed;
					long remainingMillis = (long) (avgTaskTime * (totalTasks - completed));
					remainingTime = formatTime(remainingMillis);
				}
				System.out.print("\r[" + progressBar + "] " + String.format("%.2f", progressPercent)
						+ "% (" + completed + "/" + totalTasks + " tasks) - Elapsed: " + elapsedTime
						+ " | Remaining: " + remainingTime);
			}
		}, 0, 1, TimeUnit.SECONDS);
		for (int seed = 1; seed < numSeeds; seed++) {
			for (double problemsize : new double[]{25.0, 50.0}) {
				for (double bitFlipProb = 0.05; bitFlipProb <= 0.30; bitFlipProb += 0.05) {
					for (double crossoverProb = 0; crossoverProb <= 1; crossoverProb += 0.25) {
						for (double earlystop : new double[]{0.0, 1.0}) {
							final int seedFinal = seed;
							final double bitFlipProbFinal = bitFlipProb;
							final double crossoverProbFinal = crossoverProb;
							executor.submit(() -> {
								Map<String, Double> parameters = readEAParameters(args, seedFinal, bitFlipProbFinal, crossoverProbFinal);
								parameters.put(EvolutionaryAlgorithm.PROBLEM_SIZE_PARAM, problemsize);
								parameters.put(EvolutionaryAlgorithm.EARLYSTOP_PARAM, earlystop);
								Problem problem = new Onemax((int) problemsize);
								EvolutionaryAlgorithm evolutionaryAlgorithm = new EvolutionaryAlgorithm(parameters, problem);
								evolutionaryAlgorithm.run();
							});
						}
					}
				}
			}
		}
		executor.shutdown();
		try {
			if (!executor.awaitTermination(32, TimeUnit.HOURS)) {
				System.err.println("Not all tasks completed in the expected time.");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		progressMonitor.shutdown();
		System.out.println("\nAll tasks have finished!");
	}

	public static boolean deleteFolder(File folder) {
		if (!folder.exists()) return false;
		if (folder.isDirectory()) {
			File[] files = folder.listFiles();
			if (files != null) {
				for (File file : files) {
					deleteFolder(file);
				}
			}
		}
		return folder.delete();
	}

	private static Map<String, Double> readEAParameters(String[] args, int seed, double bitFlipProb, double crossoverProb) {
		Map<String, Double> parameters = new HashMap<>();
		parameters.put(EvolutionaryAlgorithm.POPULATION_SIZE_PARAM, Double.parseDouble(args[0]));
		parameters.put(EvolutionaryAlgorithm.MAX_FUNCTION_EVALUATIONS_PARAM, Double.parseDouble(args[1]));
		parameters.put(BitFlipMutation.BIT_FLIP_PROBABILITY_PARAM, bitFlipProb);
		parameters.put(EvolutionaryAlgorithm.CROSSOVER_PROBABILITY_PARAM, crossoverProb);
		parameters.put(EvolutionaryAlgorithm.RANDOM_SEED_PARAM, (double) seed);
		return parameters;
	}

	private static String formatTime(long millis) {
		long seconds = millis / 1000;
		long s = seconds % 60;
		long m = (seconds / 60) % 60;
		long h = seconds / 3600;
		return String.format("%02d:%02d:%02d", h, m, s);
	}
}
