/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.swirlds.regression;

import com.hubspot.slack.client.models.response.chat.ChatPostMessageResponse;
import com.swirlds.regression.csv.CsvReader;
import com.swirlds.regression.experiment.ExperimentSummary;
import com.swirlds.regression.jsonConfigs.AppConfig;
import com.swirlds.regression.jsonConfigs.FileLocationType;
import com.swirlds.regression.jsonConfigs.RegressionConfig;
import com.swirlds.regression.jsonConfigs.SavedState;
import com.swirlds.regression.jsonConfigs.TestConfig;
import com.swirlds.regression.logs.LogReader;
import com.swirlds.regression.logs.PlatformLogParser;
import com.swirlds.regression.logs.StdoutLogParser;
import com.swirlds.regression.slack.SlackNotifier;
import com.swirlds.regression.slack.SlackTestMsg;
import com.swirlds.regression.testRunners.TestRun;
import com.swirlds.regression.validators.BlobStateValidator;
import com.swirlds.regression.validators.EventStreamValidator;
import com.swirlds.regression.validators.HapiClientData;
import com.swirlds.regression.validators.services.HGCAAValidator;
import com.swirlds.regression.validators.EventStreamValidator;
import com.swirlds.regression.validators.HapiClientData;
import com.swirlds.regression.validators.MemoryLeakValidator;
import com.swirlds.regression.validators.NodeData;
import com.swirlds.regression.validators.ReconnectValidator;
import com.swirlds.regression.validators.RecordStreamValidator;
import com.swirlds.regression.validators.RecoverStateValidator;
import com.swirlds.regression.validators.StandardValidator;
import com.swirlds.regression.validators.StandardValidator;
import com.swirlds.regression.validators.StreamType;
import com.swirlds.regression.validators.Validator;
import com.swirlds.regression.validators.ValidatorFactory;
import com.swirlds.regression.validators.ValidatorType;
import com.swirlds.regression.validators.services.HGCAAValidator;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.MemoryMappedFileAppender;
import org.apache.logging.log4j.core.appender.RandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.swirlds.regression.ExperimentServicesHelper.STARTING_CRYPTO_ACCOUNT;
import static com.swirlds.regression.RegressionUtilities.CHECK_BRANCH_CHANNEL;
import static com.swirlds.regression.RegressionUtilities.CHECK_USER_EMAIL_CHANNEL;
import static com.swirlds.regression.RegressionUtilities.CONFIG_FILE;
import static com.swirlds.regression.RegressionUtilities.FALL_BEHIND_MSG;
import static com.swirlds.regression.RegressionUtilities.INSIGHT_CMD;
import static com.swirlds.regression.RegressionUtilities.JAVA_PROC_CHECK_INTERVAL;
import static com.swirlds.regression.RegressionUtilities.JVM_OPTIONS_GC_LOG;
import static com.swirlds.regression.RegressionUtilities.MILLIS;
import static com.swirlds.regression.RegressionUtilities.MS_TO_NS;
import static com.swirlds.regression.RegressionUtilities.OUTPUT_LOG_FILENAME;
import static com.swirlds.regression.RegressionUtilities.POSTGRES_WAIT_MILLIS;
import static com.swirlds.regression.RegressionUtilities.PRIVATE_IP_ADDRESS_FILE;
import static com.swirlds.regression.RegressionUtilities.PTD_LOG_SUCCESS_OR_FAIL_MESSAGES;
import static com.swirlds.regression.RegressionUtilities.PUBLIC_IP_ADDRESS_FILE;
import static com.swirlds.regression.RegressionUtilities.REG_GIT_BRANCH;
import static com.swirlds.regression.RegressionUtilities.REG_GIT_USER_EMAIL;
import static com.swirlds.regression.RegressionUtilities.REG_SLACK_CHANNEL;
import static com.swirlds.regression.RegressionUtilities.REMOTE_EXPERIMENT_LOCATION;
import static com.swirlds.regression.RegressionUtilities.REMOTE_SAVED_FOLDER;
import static com.swirlds.regression.RegressionUtilities.REMOTE_SWIRLDS_LOG;
import static com.swirlds.regression.RegressionUtilities.SETTINGS_FILE;
import static com.swirlds.regression.RegressionUtilities.STANDARD_CHARSET;
import static com.swirlds.regression.RegressionUtilities.STATE_SAVED_MSG;
import static com.swirlds.regression.RegressionUtilities.TAR_NAME;
import static com.swirlds.regression.RegressionUtilities.TEST_TIME_EXCEEDED_MSG;
import static com.swirlds.regression.RegressionUtilities.TOTAL_STAKES;
import static com.swirlds.regression.RegressionUtilities.USE_STAKES_IN_CONFIG;
import static com.swirlds.regression.RegressionUtilities.WRITE_FILE_DIRECTORY;
import static com.swirlds.regression.RegressionUtilities.getResultsFolder;
import static com.swirlds.regression.RegressionUtilities.getSDKFilesToDownload;
import static com.swirlds.regression.RegressionUtilities.getSDKFilesToUpload;
import static com.swirlds.regression.RegressionUtilities.importExperimentConfig;
import static com.swirlds.regression.ExperimentServicesHelper.isServicesRegression;
import static com.swirlds.regression.logs.LogMessages.CHANGED_TO_MAINTENANCE;
import static com.swirlds.regression.logs.LogMessages.PTD_SAVE_EXPECTED_MAP;
import static com.swirlds.regression.logs.LogMessages.PTD_SAVE_EXPECTED_MAP_ERROR;
import static com.swirlds.regression.logs.LogMessages.PTD_SAVE_EXPECTED_MAP_SUCCESS;
import static com.swirlds.regression.utils.FileUtils.getInputStream;
import static com.swirlds.regression.validators.StreamType.EVENT;
import static com.swirlds.regression.validators.StreamType.RECORD;
import static org.apache.commons.io.FileUtils.listFiles;

public class Experiment implements ExperimentSummary {

	private static final Logger log = LogManager.getLogger(Experiment.class);
	private static final Marker MARKER = MarkerManager.getMarker("REGRESSION_TESTS");
	private static final Marker ERROR = MarkerManager.getMarker("EXCEPTION");

	static final int EXPERIMENT_START_DELAY = 2;
	static final int EXPERIMENT_FREEZE_SECOND_AFTER_STARTUP = 10;


	private TestConfig testConfig;
	private RegressionConfig regConfig;
	private ConfigBuilder configFile;
	private SettingsBuilder settingsFile;
	private GitInfo git;
	private SlackNotifier slacker;

	private ZonedDateTime experimentTime = null;
	private boolean isFirstTestFinished = false;
	private CloudService cloud = null;
	private ArrayList<SSHService> sshNodes = new ArrayList<>();
	private ArrayList<SSHService> testClientNodes = new ArrayList<>();
	private NodeMemory nodeMemoryProfile;
	//Executor of thread pool to run ssh session
	private ExecutorService es;
	//Whether use thread pool to run ssh session in parallel mode
	private boolean useThreadPool = false;

	// Is used for validating reconnection after running startFromX test, should be the max value of roundNumber of
	// savedState the nodes start from;
	// At the end of the test, if the last entry of roundSup of reconnected node is less than this value, the reconnect
	// is considered to be invalid
	private double savedStateStartRoundNumber = 0;

	protected boolean warnings = false;
	protected boolean errors = false;
	protected boolean exceptions = false;
	protected String slackLink = null;

	private ExperimentLocalFileHelper experimentLocalFileHelper;
	private ExperimentServicesHelper experimentServicesHelper;

	@Override
	public boolean hasWarnings() {
		return warnings;
	}

	@Override
	public boolean hasErrors() {
		return errors;
	}

	@Override
	public boolean hasExceptions() {
		return exceptions;
	}

	@Override
	public String getSlackLink() {
		return slackLink;
	}

	public void setSlackLink(String slackLink) {
		this.slackLink = slackLink;
	}

	public ArrayList<SSHService> getSSHNodes() {
		return sshNodes;
	}

	public ArrayList<SSHService> getTestClientNodes() {
		return testClientNodes;
	}

	public void setUseThreadPool(boolean useThreadPool) {
		log.info(MARKER, "Set useThreadPool as {}", useThreadPool);
		this.useThreadPool = useThreadPool;
	}

	void setExperimentTime() {
		this.experimentTime = ZonedDateTime.now(ZoneOffset.ofHours(0));
		this.experimentLocalFileHelper.setExperimentTime(experimentTime);
	}

	public ZonedDateTime getExperimentTime() {
		return experimentTime;
	}

	public void setExperimentTime(ZonedDateTime regressionStart) {
		this.experimentTime = regressionStart;
		this.experimentLocalFileHelper.setExperimentTime(experimentTime);
	}

// The below method was used for testing, and might be useful in the future

/*	public static void main(String[] args) {
		Experiment experiment = new Experiment(
				RegressionUtilities.importRegressionConfig("./configs/AwsRegressionCfgLazar.json"),
				"configs/testFcm1KSavedStateCfg.json");
		experiment.experimentTime = ZonedDateTime.of(
				2019, 10, 17, 9, 27, 0, 0,
				ZoneOffset.ofHours(0));

		experiment.testConfig.setResultFiles(Collections.singletonList("swirlds.log"));
		experiment.sshNodes = new ArrayList<SSHService>(Arrays.asList(new SSHService[] { null, null, null, null }));

		experiment.validateTest();
	}*/

	public Experiment(RegressionConfig regressionConfig, String experimentFileLocation) {
		this(regressionConfig, importExperimentConfig(experimentFileLocation));
	}

	public Experiment(RegressionConfig regressionConfig, TestConfig experiment) {
		experimentLocalFileHelper = new ExperimentLocalFileHelper(regressionConfig, experiment);
		this.regConfig = regressionConfig;
		setupTest(experiment);

		if (regConfig.getSlack() != null) {
			slacker = SlackNotifier.createSlackNotifier(regConfig.getSlack().getToken());
		}
	}

	@Override
	public String getName() {
		return testConfig.getName();
	}

	public void runLocalExperiment(GitInfo git) {
		this.git = git;
		if (regConfig.getLocal() != null) {
			startLocalTest();
		} else if (regConfig.getCloud() != null) {
			log.error(MARKER, "cloud based test are still a work in progress");
		} else {
			log.error(MARKER,
					"regression must either startService local or on cloud, please set up regression config to " +
							"reflect" +
							" " +
							"this.");
		}
	}

	/**
	 * Use thread pool to finish a list of runnable task in parallel fashion.
	 * thread pool size depends on the number of CPU cores
	 *
	 * @param tasks
	 * 		A list of runnable task
	 */
	protected void threadPoolService(List<Runnable> tasks) {
		if (useThreadPool) {
			if (tasks.size() > 0) {
				if (es == null) {
					/* this allows the same threadpool to be used for all experiments instead of creating and destroying
					 them each time */
					es = ThreadPool.getESInstance();
				}
				//Wait all thread future done
				CompletableFuture<?>[] futures = tasks.stream()
						.map(task -> CompletableFuture.runAsync(task, es).orTimeout(3600, TimeUnit.SECONDS))
						.toArray(CompletableFuture[]::new);
				CompletableFuture.allOf(futures).orTimeout(3600, TimeUnit.SECONDS).join();
			}
		}
		/* run tasks sequentially on main thread if thread pool use is not requested */
		else {
			for (Runnable task : tasks) {
				task.run();
			}
		}
	}

	public void startAllSwirlds() {
		threadPoolService(sshNodes.stream().<Runnable>map(node -> () -> {
			node.execWithProcessID(getJVMOptionsString());
			log.info(MARKER, "node:{} swirlds.jar started.", node.getIpAddress());
		}).collect(Collectors.toList()));
	}

	/**
	 * Start Browser and HederaNode.jar. When they start running , start SuiteRunner.jar to run Services regression
	 */
	public void startServicesRegression(boolean startSuiteRunner) {
		experimentServicesHelper.startServicesRegression(startSuiteRunner);
	}

	public void stopAllSwirlds() {
		threadPoolService(sshNodes.stream().<Runnable>map(currentNode -> currentNode::killJavaProcess)
				.collect(Collectors.toList()));
	}

	public void stopLastSwirlds() {
		SSHService nodeToKill = sshNodes.get(getLastStakedNode());
		if (testConfig.getReconnectConfig().isKillNetworkReconnect()) {
			nodeToKill.killNetwork();
		} else {
			nodeToKill.killJavaProcess();
		}
	}

	public void startLastSwirlds() {
		SSHService nodeToReconnect = sshNodes.get(getLastStakedNode());
		if (testConfig.getReconnectConfig().isKillNetworkReconnect()) {
			nodeToReconnect.reviveNetwork();
		} else {
			nodeToReconnect.execWithProcessID(getJVMOptionsString());
		}
	}

	public void sleepThroughExperiment(long testDuration) {
		/*TODO: Test duration shouldn't be needed for PTD now, look into removing it for PTD tests */
        /* Local test shouldn't look for dead nodes, if the test is too short this would just make the test run
        longer */
		if (regConfig.getCloud() != null && JAVA_PROC_CHECK_INTERVAL < testDuration) {
			ArrayList<Boolean> isProcDown = new ArrayList<>();
			ArrayList<Boolean> isTestFinished = new ArrayList<>();
			/* set array lists to appropriate size with default values */
			for (int i = 0; i < sshNodes.size(); i++) {
				isProcDown.add(false);
				isTestFinished.add(false);
			}
			/* testDuration is already in milliseconds */
			/* TODO: endTime should be unnecessary if java Proc check and test success are working for PTD */
			long endTime = System.nanoTime() + (testDuration * MS_TO_NS);
			log.info(MARKER, "sleeping for {} seconds, or until test finishes ", testDuration / MILLIS);
			while (System.nanoTime() < endTime) { // Don't go over set test time
				try {
					log.trace(MARKER, "sleeping for {} seconds ", JAVA_PROC_CHECK_INTERVAL / MILLIS);
					Thread.sleep(JAVA_PROC_CHECK_INTERVAL);
				} catch (InterruptedException e) {
					log.error(ERROR, "could not sleep.", e);
				}
				/* Check all processes are still running, then check if test has finished */
				for (int i = 0; i < sshNodes.size(); i++) {
					SSHService node = sshNodes.get(i);
					boolean isProcStillRunning = node.checkProcess();
					/* if this it the first time down, keep running, if down two times in a row, stop */
					if (!isProcStillRunning && isProcDown.get(i)) {
						return;
					} else {
                        /* always set to true in case it was false, this makes sure reconnect and restart don't fail out
                        after the first reconnect/restart */
						isProcDown.set(i, !isProcStillRunning);
					}
					isTestFinished.set(i, node.isTestFinished());
				}
				/* we don't want to exit if only a few nodes have reported being finished */
				if (checkForAllTrue(isTestFinished)) {
					return;
				}
			}
			log.trace(MARKER, TEST_TIME_EXCEEDED_MSG);
		} else {
			try {
				//TODO: should this check for test finishing as well for local test > JAVA_PROC_CHECK_INTERVAL?
				log.info(MARKER, "sleeping for {} seconds ", testDuration / MILLIS);
				Thread.sleep(testDuration);
			} catch (InterruptedException e) {
				log.error(ERROR, "could not sleep.", e);
			}
		}
	}

	/**
	 * Sleep until reached the testDuration, or one of checker callback return true
	 *
	 * @param testDuration
	 * 		Default test duration, in the unit of milliseconds, to run if none of checker return true
	 * @param checkerList
	 * 		A list of callback functions to check whether the conditions of exiting sleep mode have been met
	 */
	public void sleepThroughExperimentWithCheckerList(long testDuration,
			List<BooleanSupplier> checkerList) {
		if (regConfig.getCloud() != null && JAVA_PROC_CHECK_INTERVAL < testDuration) {

			long endTime = System.nanoTime() + (testDuration * MS_TO_NS);
			log.info(MARKER, "sleeping for {} seconds, or until condition met ", testDuration / MILLIS);

			while (System.nanoTime() < endTime) { // Don't go over set test time
				try {
					log.trace(MARKER, "sleeping for {} seconds ", JAVA_PROC_CHECK_INTERVAL / MILLIS);
					Thread.sleep(JAVA_PROC_CHECK_INTERVAL);
				} catch (InterruptedException e) {
					log.error(ERROR, "could not sleep.", e);
				}

				if (checkerList != null) {
					for (BooleanSupplier checker : checkerList) {
						if (checker != null) {
							if (checker.getAsBoolean()) {
								return; //exit both for and while loop
							}
						}
					}
				}
			}
			log.trace(MARKER, TEST_TIME_EXCEEDED_MSG);
		} else {
			try {
				log.info(MARKER, "sleeping for {} seconds ", testDuration / MILLIS);
				Thread.sleep(testDuration);
			} catch (InterruptedException e) {
				log.error(ERROR, "could not sleep.", e);
			}
		}
	}

	/**
	 * Whether all node find defined number of messages in log file
	 *
	 * @param msgList
	 * 		A list of message to search for
	 * @param messageAmount
	 * 		How many times the message expected to appear
	 * @param fileName
	 * 		File name to search for the message
	 * @return return true if the time that the message appeared is equal or larger than messageAmount
	 */
	public boolean isAllNodesFoundEnoughMessage(List<String> msgList, int messageAmount, String fileName) {
		boolean isEnoughMsgFound = false;
		for (int i = 0; i < sshNodes.size(); i++) {
			SSHService node = sshNodes.get(i);
			if (node.countSpecifiedMsg(msgList, fileName) == messageAmount) {
				log.info(MARKER, "Node {} found enough message {}", i, msgList);
				isEnoughMsgFound = true;
			} else {
				isEnoughMsgFound = false;
			}
		}
		return isEnoughMsgFound;
	}

	/**
	 * Whether any node find at least one of the message
	 *
	 * @param msgList
	 * 		A list of message to search for
	 * @param fileName
	 * 		File name to search for the message
	 * @return return true if found at least one occurrence
	 */
	public boolean isAnyNodeFoundMessage(List<String> msgList, String fileName) {
		for (int i = 0; i < sshNodes.size(); i++) {
			SSHService node = sshNodes.get(i);
			if (node.countSpecifiedMsg(msgList, fileName) > 0) {
				log.info(MARKER, "Node {} found at least one message {}", i, msgList);
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether all nodes backed up the last round
	 *
	 * @param fileName
	 * 		File name to search for the saved folder containing rounds relative to
	 * @return return true if the time that the message appeared is equal or larger than messageAmount
	 */
	public boolean isAllNodesBackedUpLastRound(String fileName) {
		Long maxRoundSeen = -1L;
		Boolean allCompleteMaxRound = true;
		HashMap<Integer, HashMap<Long, Boolean>> nodeRounds = new HashMap<>();
		HashMap<Integer, SSHService> nodesStillSaving = new HashMap<>();

		log.info(MARKER, "Checking if all nodes backed up last round");

		for (int i = 0; i < sshNodes.size(); i++) {
			SSHService node = sshNodes.get(i);
			HashMap<Long, Boolean> ssProgress = node.checkSavedStateProgress(fileName);

			if (ssProgress == null) {
				log.info(MARKER, "no saved nodes found for node {}", i);
				continue;
			}

			nodeRounds.put(i, ssProgress);

			for (Map.Entry<Long, Boolean> entry : ssProgress.entrySet()) {
				Long roundNum = entry.getKey();
				Boolean roundComplete = entry.getValue();

				//reset tracking for max round seen
				if (roundNum > maxRoundSeen) {
					maxRoundSeen = roundNum;
					allCompleteMaxRound = roundComplete;

					nodesStillSaving = new HashMap<>();
				}

				if (roundNum == maxRoundSeen) {
					allCompleteMaxRound &= roundComplete;
					if (roundComplete == false) {
						nodesStillSaving.put(i, node);
					}
				}
			}
		}

		//kill java process on nodes that have finished the last round
		for (Map.Entry<Integer, HashMap<Long, Boolean>> entry : nodeRounds.entrySet()) {
			Integer nodeIndex = entry.getKey();
			SSHService node = sshNodes.get(nodeIndex);
			HashMap<Long, Boolean> nodeRoundMap = entry.getValue();

			if (nodeRoundMap.containsKey(maxRoundSeen)) {
				//value looked up is the boolean value telling whether the round is completed
				if (!nodeRoundMap.get(maxRoundSeen)) {
					nodesStillSaving.put(nodeIndex, node);
				}
			} else {
				nodesStillSaving.put(nodeIndex, node);
			}
		}

		if (allCompleteMaxRound == false) {
			//retry all nodes that had not completed saving the final round
			allCompleteMaxRound = true;

			try {
				log.trace(MARKER, "sleeping for {} seconds ", testConfig::getSaveStateCheckWait);
				Thread.sleep(testConfig.getSaveStateCheckWait());
			} catch (InterruptedException e) {
				log.error(ERROR, "could not sleep.", e);
			}

			for (Map.Entry<Integer, SSHService> entry : nodesStillSaving.entrySet()) {
				Integer nodeNum = entry.getKey();
				SSHService node = entry.getValue();
				HashMap<Long, Boolean> nodeSaveStatus = node.checkSavedStateProgress(fileName);

				if (nodeSaveStatus.containsKey(maxRoundSeen)) {
					if (!nodeSaveStatus.get(maxRoundSeen)) {
						log.error(ERROR, "node {} did not contain saved state and postgres backup for round {}",
								nodeNum,
								maxRoundSeen);
						allCompleteMaxRound = false;
					}
				} else {
					allCompleteMaxRound = false;
				}
			}
		}

		if (allCompleteMaxRound) {
			log.info(MARKER, "All nodes finishes saving up to round " + maxRoundSeen);
		} else {
			log.error(ERROR, "Not all nodes finished saving for round " + maxRoundSeen);
		}

		return allCompleteMaxRound;
	}

	/**
	 * Whether test finished message can be found at least twice in the log file
	 */
	public boolean isFoundTwoPTDFinishMessage() {
		return isAllNodesFoundEnoughMessage(PTD_LOG_SUCCESS_OR_FAIL_MESSAGES, 2, REMOTE_SWIRLDS_LOG);
	}

	/**
	 * Whether state recover finished message found in the log file
	 */
	public boolean isFoundStateRecoverDoneMessage() {
		return isAllNodesFoundEnoughMessage(Collections.singletonList(STATE_SAVED_MSG), 1, REMOTE_SWIRLDS_LOG);
	}

	/**
	 * Whether any node found fall behind message
	 */
	public boolean isAnyNodeFoundFallBehindMessage() {
		return isAnyNodeFoundMessage(Collections.singletonList(FALL_BEHIND_MSG), REMOTE_SWIRLDS_LOG);
	}


	public boolean isProcessFinished() {
		ArrayList<Boolean> isProcDown = new ArrayList<>();
		ArrayList<Boolean> isTestFinished = new ArrayList<>();
		/* set array lists to appropriate size with default values */
		for (int i = 0; i < sshNodes.size(); i++) {
			isProcDown.add(false);
			isTestFinished.add(false);
		}

		/* Check all processes are still running, then check if test has finished */
		for (int i = 0; i < sshNodes.size(); i++) {
			SSHService node = sshNodes.get(i);
			boolean isProcStillRunning = node.checkProcess();
			/* if this it the first time down, keep running, if down two times in a row, stop */
			if (!isProcStillRunning && isProcDown.get(i)) {
				return true;
			} else {
				isProcDown.set(i, !isProcStillRunning);
			}
			isTestFinished.set(i, node.isTestFinished());
		}
		/* we don't want to exit if only a few nodes have reported being finished */
		if (checkForAllTrue(isTestFinished)) {
			return true;
		}
		return false;
	}

	private boolean checkForAllTrue(ArrayList<Boolean> isTestFinished) {
		for (Boolean val : isTestFinished) {
			if (!val) {
				return false;
			}
		}
		return true;
	}

	protected void sendTarToNode(SSHService currentNode, Collection<File> filesToSend) {
		String pemFile = getPEMFile();
		String pemFileName = pemFile.substring(pemFile.lastIndexOf('/') + 1);
		long startTime = System.nanoTime();
		File oldTarFile = new File(TAR_NAME);
		if (oldTarFile.exists()) {
			oldTarFile.delete();
		}
		currentNode.createNewTar(filesToSend);
		ArrayList<File> tarball = new ArrayList<>();
		tarball.add(new File(TAR_NAME));
		currentNode.scpToSpecificFiles(tarball);
		currentNode.extractTar();
		currentNode.executeCmd("chmod 400 ~/" + REMOTE_EXPERIMENT_LOCATION + pemFileName);
		long endTime = System.nanoTime();
		log.trace(MARKER, "Took {} seconds to upload tarball", (endTime - startTime) / 1000000000);
	}

	private Collection<File> gatherFilesToSendToNode(ArrayList<File> addedFiles) {
		return getSDKFilesToUpload(
				new File(getPEMFile()), new File(testConfig.getLog4j2File()), addedFiles);
	}

	public String getPEMFile() {
		return regConfig.getCloud().getKeyLocation() + ".pem";
	}

	@Override
	public String getUniqueId() {
		Integer hash = (RegressionUtilities.getExperimentTimeFormattedString(getExperimentTime()) + "-" +
				getName()).hashCode();
		if (hash < 0) {
			hash *= -1;
		}
		return hash.toString();
	}


	private List<NodeData> loadNodeData(String directory) {
		int numberOfNodes;
		if (regConfig.getLocal() != null) {
			numberOfNodes = regConfig.getLocal().getNumberOfNodes();
		} else if (sshNodes == null || sshNodes.isEmpty()) {
			return null;
		} else {
			numberOfNodes = sshNodes.size();
		}
		List<NodeData> nodeData = new ArrayList<>();
		for (int i = 0; i < numberOfNodes; i++) {
			for (String logFile : testConfig.getResultFiles()) {
				String logFileName = experimentLocalFileHelper.getExperimentResultsFolderForNode(i) + logFile;
				InputStream logInput = getInputStream(logFileName);

				String csvFileName = settingsFile.getSettingValue("csvFileName") + i + ".csv";
				String csvFilePath = experimentLocalFileHelper.getExperimentResultsFolderForNode(i) + csvFileName;
				InputStream csvInput = getInputStream(csvFilePath);
				LogReader logReader = null;
				if (logInput != null) {
					logReader = LogReader.createReader(PlatformLogParser.createParser(1), logInput);
				}
				CsvReader csvReader = null;
				if (csvInput != null) {
					csvReader = CsvReader.createReader(1, csvInput);
				}
				String outputLogFileName = experimentLocalFileHelper.getExperimentResultsFolderForNode(
						i) + OUTPUT_LOG_FILENAME;
				InputStream outputLogInput = getInputStream(outputLogFileName);
				LogReader outputLogReader = null;
				if (outputLogInput != null) {
					outputLogReader = LogReader.createReader(new StdoutLogParser(), outputLogInput);
				}

				nodeData.add(new NodeData(logReader, csvReader, outputLogReader));
			}
		}
		return nodeData;
	}

	private void validateTest() {
		SlackTestMsg slackMsg = new SlackTestMsg(
				getUniqueId(),
				regConfig,
				testConfig,
				getResultsFolder(experimentTime, regConfig.getName()),
				git
		);

		// if posting to the regression channel, check branch and git user
		if (regConfig.getSlack() != null
				&& regConfig.getSlack().getChannel().equals(REG_SLACK_CHANNEL)) {
			if (CHECK_BRANCH_CHANNEL
					&& !git.getGitInfo(true).contains(REG_GIT_BRANCH)) {
				slackMsg.addWarning(String.format(
						"Only nightly tests on '%s' branch should be posted to the '%s' channel",
						REG_GIT_BRANCH,
						REG_SLACK_CHANNEL
				));
			}
			if (CHECK_USER_EMAIL_CHANNEL
					&& !git.getUserEmail().equals(REG_GIT_USER_EMAIL)) {
				slackMsg.addWarning(String.format(
						"The git user with the email '%s' should not be posting to the '%s' channel",
						git.getUserEmail(),
						REG_SLACK_CHANNEL
				));
			}
		}

		// If the test contains reconnect, we don't use StreamingServerValidator, because during the reconnect the
		// event streams wont be generated, which would cause mismatch in evts files on the nodes
		boolean reconnect = false;

		// Build a lists of validator
		List<Validator> requiredValidator = new ArrayList<>();
		for (ValidatorType item : testConfig.validators) {
			if (!reconnect && item.equals(ValidatorType.RECONNECT)) {
				reconnect = true;
			}

			List<NodeData> nodeData = loadNodeData(testConfig.getName());
			if (nodeData == null || nodeData.size() == 0) {
				slackMsg.addError("No data found for " + item);
				continue;
			} else if (nodeData.size() < regConfig.getTotalNumberOfNodes()) {
				slackMsg.addWarning(String.format(
						"%s - Configured number of nodes is %d, but only found %d nodes",
						item,
						regConfig.getTotalNumberOfNodes(),
						nodeData.size()));
			}

			List<HapiClientData> testClientNodeData = new ArrayList<>();
			if (isServicesRegression() && item == ValidatorType.HAPI_CLIENT) {
				testClientNodeData = experimentServicesHelper.
						loadTestClientNodeData(testClientNodes);
			}

			List<NodeData> hederaNodeHGCAAData = new ArrayList<>();
			if (isServicesRegression() && item == ValidatorType.HGCAA) {
				hederaNodeHGCAAData = experimentServicesHelper.
						loadHederaNodeHGCAAData(sshNodes);
			}

			Validator validatorToAdd = ValidatorFactory.getValidator(item,
					nodeData,
					testConfig,
					// service test does not generated expectedMap file
					isServicesRegression() ? null : experimentLocalFileHelper.loadExpectedMapPaths(),
					testClientNodeData, hederaNodeHGCAAData);

			if (item == ValidatorType.BLOB_STATE) {
				((BlobStateValidator) validatorToAdd).setExperimentFolder(
						experimentLocalFileHelper.getExperimentFolder());
			}
			requiredValidator.add(validatorToAdd);
		}
		List<NodeData> nodeData = loadNodeData(testConfig.getName());
		requiredValidator.add(ValidatorFactory.getValidator(ValidatorType.STDOUT, nodeData, testConfig));

		// Add stream server validator if event streaming is configured
		if (regConfig.getEventFilesWriters() > 0) {
			EventStreamValidator eventStreamValidator = new EventStreamValidator(
					experimentLocalFileHelper.loadStreamingServerData(EVENT), reconnect);
			if (testConfig.getRunType() == RunType.RECOVER) {
				eventStreamValidator.setStateRecoverMode(true);
			}

			requiredValidator.add(eventStreamValidator);
		}

		// Add record server validator if record streaming is configured
		if (regConfig.getRecordFilesWriters() > 0) {
			RecordStreamValidator ssValidator = new RecordStreamValidator(
					experimentLocalFileHelper.loadStreamingServerData(RECORD));

			requiredValidator.add(ssValidator);
		}

		if (testConfig.getMemoryLeakCheckConfig() != null) {
			requiredValidator.add(
					new MemoryLeakValidator(
							testConfig.getMemoryLeakCheckConfig(),
							experimentLocalFileHelper.getResultFolders()));
		}

		for (Validator item : requiredValidator) {
			try {
				if (item instanceof ReconnectValidator) {
					((ReconnectValidator) item).setSavedStateStartRoundNumber(savedStateStartRoundNumber);
				}
				if (item instanceof HGCAAValidator) {
					// if testing update feature, hedera service will restart after freeze
					if (this.testConfig.getHederaServicesConfig().getTestSuites().contains("UpdateServerFiles")) {
						((HGCAAValidator) item).setCheckHGCAppRestart(true);
					}
				}
				if (item instanceof StandardValidator && regConfig.getNetErrorCfg() != null) {
					((StandardValidator) item).setIgnoreSyncException(true);
				}
				if (isServicesRegression() && item instanceof RecoverStateValidator) {
					((RecoverStateValidator)item).setServiceRegression(true);
				}
				item.setLastStakedNode(getLastStakedNode());
				item.validate();
				slackMsg.addValidatorInfo(item);
			} catch (Throwable e) {
				log.error(ERROR, "{} validator failed to validate", item.getClass(), e);
				slackMsg.addValidatorException(item, e);
			}
		}
		sendSlackMessage(slackMsg, regConfig.getSlack().getChannel());
		log.info(MARKER, slackMsg.getPlaintext());
		createStatsFile(experimentLocalFileHelper.getExperimentFolder());
		sendSlackStatsFile(new SlackTestMsg(getUniqueId(), regConfig, testConfig), "./multipage_pdf.pdf");

		// TODO Experiments only communicate failures over the slack message
		// TODO This should be fixed
		warnings = warnings || slackMsg.hasWarnings();
		errors = errors || slackMsg.hasErrors();
		exceptions = exceptions || slackMsg.hasExceptions();
	}

	private int getLastStakedNode() {
		return sshNodes.size() - regConfig.getNumberOfZeroStakeNodes() - 1;
	}

	private void createStatsFile(String resultsFolder) {
		String csvName = settingsFile.getSettingValue("csvFileName");
		String[] insightCmd = String.format(INSIGHT_CMD, RegressionUtilities.getPythonExecutable(),
				RegressionUtilities.INSIGHT_SCRIPT_LOCATION, resultsFolder, csvName).split(" ");
		ExecStreamReader.outputProcessStreams(insightCmd);

	}

	public void sendSettingFileToNodes() {
		if (regConfig.getNumberOfZeroStakeNodes() > 0) {
			settingsFile.addSetting("enableBetaMirror", "1");
		}

		if (regConfig.getEventFilesWriters() == 0) {
			sendSettingToNonStreamingNodes();
		} else {
			sendSettingToStreamingNodes();
		}
	}

	private void sendSettingToStreamingNodes() {
		/* since the contents of the file change, but not the location of the file this can be set up outside the for
		loop */
		ArrayList<File> newUploads = new ArrayList<>();
		newUploads.add(new File(WRITE_FILE_DIRECTORY + SETTINGS_FILE));
		for (int i = 0; i < sshNodes.size(); i++) {
			if (i == 0 && i < regConfig.getEventFilesWriters()) {
				/* create settings file with enableEventStreaming enabled for streaming nodes */
				ArrayList<String> enableEventStreamSetting = new ArrayList<>();
				enableEventStreamSetting.add("enableEventStreaming, true");
				settingsFile.exportNodeSpecificSettingsFile(enableEventStreamSetting);
			} else if (i == regConfig.getEventFilesWriters()) {
				/* now that streaming nodes are taken care of  export settings file without enableEventStreaming
				setting */
				settingsFile.exportSettingsFile();
			}
			sshNodes.get(i).scpToSpecificFiles(newUploads);
		}
	}

	private void sendSettingToNonStreamingNodes() {
		settingsFile.exportSettingsFile();
		ArrayList<File> newUploads = new ArrayList<>();
		newUploads.add(new File(WRITE_FILE_DIRECTORY + SETTINGS_FILE));
		threadPoolService(
				sshNodes.stream().<Runnable>map(currentNode -> () -> currentNode.scpToSpecificFiles(newUploads)).collect(
						Collectors.toList()));
	}

	private void setIPsAndStakesInConfig() {
		configFile.setPublicIPList(cloud.getPublicIPList());
		configFile.setPrivateIPList(cloud.getPrivateIPList());
		if (USE_STAKES_IN_CONFIG) {
			List<Long> stakes = new ArrayList<>();

			if (regConfig.getNumberOfZeroStakeNodes() > 0) {
				log.debug("Running with Zero Stake Nodes [ zeroStake = {}, totalNodes = {} ]",
						regConfig::getNumberOfZeroStakeNodes, regConfig::getTotalNumberOfNodes);
				final int totalStakedNodes =
						(regConfig.getTotalNumberOfNodes() - regConfig.getNumberOfZeroStakeNodes());
				final long stakePerActiveNode =
						TOTAL_STAKES / totalStakedNodes;

				stakes.addAll(Collections.nCopies(totalStakedNodes, stakePerActiveNode));

				for (int i = 0; i < regConfig.getNumberOfZeroStakeNodes(); i++) {
					stakes.add(0L);
				}
			} else {
				log.debug("Running with Normal Nodes [ totalNodes = {} ]", regConfig::getTotalNumberOfNodes);
				// each node gets the same stake
				stakes.addAll(Collections.nCopies(
						regConfig.getTotalNumberOfNodes(),
						TOTAL_STAKES / regConfig.getTotalNumberOfNodes()));
			}

			configFile.setStakes(stakes);
		}

		// If it is services-regression set starting crypto account as 3
		if (isServicesRegression()) {
			configFile.setStartingCryptoAccount(STARTING_CRYPTO_ACCOUNT);
		}

		configFile.exportConfigFile();

		//set the built public Ip string with crypto accounts
		if (isServicesRegression()) {
			ExperimentServicesHelper.setPublicIPStringForServices(configFile.getPublicIPsWithCryptoAccounts());
		}
	}

	public void sendConfigToNodes() {
		// pub and private address aren't set in configFile until this call, since it is never needed/used.
		setIPsAndStakesInConfig();

		ArrayList<File> newUploads = new ArrayList<>();
		newUploads.add(new File(WRITE_FILE_DIRECTORY + CONFIG_FILE));
		threadPoolService(sshNodes.stream()
				.<Runnable>map(currentNode -> () -> currentNode.scpToSpecificFiles(newUploads))
				.collect(Collectors.toList()));
	}

	public void sendSlackMessage(SlackTestMsg msg, String channel) {
		ChatPostMessageResponse response = slacker.messageChannel(msg, channel);
		this.setSlackLink(slacker.getLinkTo(response));
	}

	public void sendSlackMessage(String error, String channel) {
		SlackTestMsg msg = new SlackTestMsg(getUniqueId(), regConfig, testConfig);
		msg.addError(error);
		sendSlackMessage(msg, channel);
	}

	public void sendSlackStatsFile(SlackTestMsg msg, String fileLocation) {
		slacker.uploadFile(msg, fileLocation, testConfig.getName());
	}

	/**
	 * Confirms that all nodes can be connected to, and that sets them up and prepares them for the experiment.
	 *
	 * @throws IOException
	 * 		- SocketException (child of IOException) is thrown if unable to connect to node.
	 */
	void setupSSHServices() throws IOException {
		String login = regConfig.getCloud().getLogin();
		File keyfile = new File(regConfig.getCloud().getKeyLocation() + ".pem");
		setUpSSHServicesForInstances(login, keyfile);

		if (isServicesRegression()) {
			setUpSSHServicesForInstances(login, keyfile, true);
		}
	}

	private void setUpSSHServicesForInstances(String login, File keyfile) throws IOException {
		setUpSSHServicesForInstances(login, keyfile, false);
	}

	private void setUpSSHServicesForInstances(String login, File keyfile, boolean isTestClient) throws IOException {
		//TODO multi-thread this perhaps?
		ArrayList<String> publicIPList;
		if (isTestClient) {
			publicIPList = cloud.getPublicIPListOfTestClients();
		} else {
			publicIPList = cloud.getPublicIPList();
		}

		int nodeNumber = publicIPList.size();
		for (int i = 0; i < nodeNumber; i++) {
			String pubIP = publicIPList.get(i);
			SSHService currentNode = new SSHService(login, pubIP, keyfile);
			//TODO Unit test for this must change the files name of the pem file to prevent connections
			if (currentNode == null) {
				cloud.destroyInstances();
				log.error(ERROR, "Could not start/ or connect to node, exiting regression test.");
				uploadFilesToSharepoint();
				throw new SocketException("Node: " + pubIP + " returned as null on initialization.");
			}
			currentNode.reset();
			cloud.memoryNeedsForAllNodes = new NodeMemory(currentNode.checkTotalMemoryOnNode());
			currentNode.adjustNodeMemoryAllocations(cloud.memoryNeedsForAllNodes);

			if (isTestClient) {
				testClientNodes.add(currentNode);
			} else {
				sshNodes.add(currentNode);
			}
		}
	}

	public SavedState getSavedStateForNode(int nodeIndex, int totalNodes) {
		List<SavedState> all = Stream.of(Collections.singletonList(testConfig.getStartSavedState()),
				testConfig.getStartSavedStates())
				.filter(Objects::nonNull)
				.flatMap(Collection::stream)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		for (SavedState ss : all) {
			if (ss.getNodeIdentifier().isNodeInGroup(nodeIndex, totalNodes, getLastStakedNode())) {
				log.info(MARKER, "SAVED STATE RESTORE for Node {} returned true", nodeIndex);
				return ss;
			}
		}

		return null;
	}

	void runRemoteExperiment(CloudService cld, GitInfo git) throws IOException {
		experimentServicesHelper = new ExperimentServicesHelper(this);
		//TODO: unit test for null cld
		if (cld == null) {
			log.error(ERROR, "Cloud instance was null, cannot run test!");
			sendSlackMessage("Cloud instance was null, cannot start test!", regConfig.getSlack().getChannel());
			return;
		}
		this.cloud = cld;
		this.git = git;
		this.cloud.setInstanceNames(testConfig.getName());
		exportIPAddressFiles();

		setIPsAndStakesInConfig();
		setupSSHServices();
		int nodeNumber = sshNodes.size();
		ArrayList<File> addedFiles = buildAdditionalFileList();

		if (regConfig.getNetErrorCfg() != null) {
			addedFiles.add(new File("src/main/resources/block_sync_port.sh"));
		}
		//Step 1, send tar to node 0
		Collection<File> filesToSend;
		final SSHService firstNode = sshNodes.get(0);
		calculateNodeMemoryProfile(firstNode);

		if (isServicesRegression()) {
			filesToSend = experimentServicesHelper.getListOfFilesToSend(
					false, new File(getPEMFile()), addedFiles);
		} else {
			filesToSend = gatherFilesToSendToNode(addedFiles);
		}

		sendTarToNode(firstNode, filesToSend);

		//Step 2, node 0 use a rsync to send files to other nodes in parallel mode
		//ATF server launch a single SSH session on node0, and node0 uses N-1 rsync sessions
		// to send files to other N-1 nodes

		// Get list of address of other N-1 nodes
		List<String> ipAddresses = IntStream.range(1, sshNodes.size()).mapToObj(
				i -> sshNodes.get(i).getIpAddress()).collect(Collectors.toList());
		firstNode.rsyncTo(addedFiles, new File(testConfig.getLog4j2File()), ipAddresses);
		log.info(MARKER, "upload to nodes has finished");

		// Send suite runner jar to test client nodes if there are any
		if (isServicesRegression() && testClientNodes.size() > 0) {
			experimentServicesHelper.sendTarToTestClientNodes(addedFiles);
		}

		//Step 3, copy saved state to nodes if necessary
		threadPoolService(IntStream.range(0, sshNodes.size())
				.<Runnable>mapToObj(i -> () -> {
					log.info(MARKER, "COPY SAVED STATE THREAD FOR NODE: {}", i);
					SSHService currentNode = sshNodes.get(i);
					// copy a saved state if set in config
					SavedState savedState = getSavedStateForNode(i, nodeNumber);
					if (savedState != null) {
						double thisSavedStateRoundNum = savedState.getRound();
						if (thisSavedStateRoundNum > savedStateStartRoundNumber) {
							savedStateStartRoundNumber = thisSavedStateRoundNum;
						}

						String ssPath = RegressionUtilities.getRemoteSavedStatePath(
								savedState.getMainClass(),
								i,
								savedState.getSwirldName(),
								savedState.getRound()
						);
						FileLocationType locationType = savedState.getLocationType();
						switch (locationType) {
							case AWS_S3:
								log.info(MARKER, "Downloading saved state for S3 to node {}", i);
								currentNode.copyS3ToInstance(savedState.getLocation(), ssPath);
								// list files in destination directory for validation
								currentNode.listDirFiles(ssPath);
								break;
							case LOCAL:
								log.info(MARKER, "Uploading local saved state to node {}", i);
								try {
									currentNode.scpFilesToRemoteDir(
											RegressionUtilities.getFilesInDir(savedState.getLocation(), true),
											ssPath
									);
								} catch (IOException e) {
									log.error(ERROR, "Fail to scp saved state from local ", e);
								}
								break;
						}
					}

				}).collect(Collectors.toList()));

		log.info(MARKER, "upload to nodes has finished");

		log.info(MARKER, "Sleeping for {} seconds to allow PostGres to start", POSTGRES_WAIT_MILLIS / MILLIS);
		//TODO add a proper check for postgres
		try {
			Thread.sleep(POSTGRES_WAIT_MILLIS);
		} catch (InterruptedException ignored) {
		}

		TestRun testRun = testConfig.getRunType().getTestRun();
		testRun.preRun(testConfig, this);
		sendSettingFileToNodes();

		setupNetworkErrorCfg();
		testRun.runTest(testConfig, this);

		cleanNetworkErrorCfg();

		if (testConfig.validators.contains(ValidatorType.BLOB_STATE)) {
			if (!isAllNodesBackedUpLastRound(REMOTE_SWIRLDS_LOG)) {
				log.error(ERROR, "Not all nodes successfully backed up last round");
			}

			scpSavedFolder();
		}

		//TODO maybe move the kill to the TestRun
		stopAllSwirlds(); //kill swirlds.jar java process

		// call badgerize.sh that tars all the database logs

		if (testConfig.getDownloadDbLogFiles()) {
			for (int i = 0; i < nodeNumber; i++) {
				SSHService currentNode = sshNodes.get(i);
				currentNode.badgerize();
			}
		}

		// generate files for validating event stream files
		// make sure that more streaming client than nodes were not requested
		int eventFileWriters = Math.min(regConfig.getEventFilesWriters(), sshNodes.size());
		createSha1SumForStreams(eventFileWriters, EVENT);

		// generate files for validating record stream files
		// make sure that more streaming client than nodes were not requested
		int recordFileWriters = Math.min(regConfig.getRecordFilesWriters(), sshNodes.size());
		createSha1SumForStreams(recordFileWriters, RECORD);

		threadPoolService(IntStream.range(0, sshNodes.size())
				.<Runnable>mapToObj(i -> () -> {
					SSHService node = sshNodes.get(i);
					boolean success = false;
					while (!success)
						success = node.scpFrom(
								experimentLocalFileHelper.getExperimentResultsFolderForNode(i),
								(ArrayList<String>) testConfig.getResultFiles());
				}).collect(Collectors.toList()));

		if (isServicesRegression() && testClientNodes.size() > 0) {
			experimentServicesHelper.scpFromTestClientNode();
		}

		log.info(MARKER, "Downloaded experiment data");

		validateTest();
		log.info(MARKER, "Test validation finished");
		uploadFilesToSharepoint();

		//resetNodes();

		killJavaProcess(); //kill any data collecting java process
	}

	/**
	 * Create sha1sum for RECORD and EVENT streams
	 *
	 * @param writers
	 * @param streamType
	 */
	private void createSha1SumForStreams(int writers, StreamType streamType) {
		threadPoolService(IntStream.range(0, writers)
				.<Runnable>mapToObj(i -> () -> {
					SSHService node = sshNodes.get(i);
					node.makeSha1sumOfStreamedEvents(i, streamType);
					log.info(MARKER, "node:" + node.getIpAddress() +
							" created sha1sum of ." + streamType.getExtension());
				}).collect(Collectors.toList()));
	}

	/**
	 * Setup network configuration used for simulating network error
	 */
	void setupNetworkErrorCfg() {
		if (regConfig.getNetErrorCfg() != null && regConfig.getNetErrorCfg().size() > 0) {
			for (int i = 0; i < regConfig.getNetErrorCfg().size(); i++) {
				SSHService node = sshNodes.get(i);
				node.setupNetworkErrorCfg(regConfig.getNetErrorCfg().get(i));
			}
		}
	}

	/**
	 * Clear network configuration used for simulating network error
	 */
	void cleanNetworkErrorCfg() {
		if (regConfig.getNetErrorCfg() != null && regConfig.getNetErrorCfg().size() > 0) {
			for (int i = 0; i < regConfig.getNetErrorCfg().size(); i++) {
				SSHService node = sshNodes.get(i);
				node.cleanNetworkErrorCfg(regConfig.getNetErrorCfg().get(i));
			}
		}
	}

	/**
	 * Calls the node to get total memory, and then sets nodeMemoryProfile to that amount.
	 * This assumes all nodes are the same type of instance.
	 *
	 * @param currentNode
	 * 		- the node to be profiled
	 */
	protected void calculateNodeMemoryProfile(SSHService currentNode) {
		String totalNodeMemory = currentNode.checkTotalMemoryOnNode();
		nodeMemoryProfile = new NodeMemory(totalNodeMemory);
	}

	void scpSavedFolder() {
		ArrayList<String> blobStateScpList = new ArrayList<>();
		blobStateScpList.add(REMOTE_SAVED_FOLDER + "$");

		//BLOB_STATE validator needs the data/saved folder add it to the list of files to retrieve
		for (int i = 0; i < sshNodes.size(); i++) {
			SSHService node = sshNodes.get(i);
			node.scpFromListOnly(
					experimentLocalFileHelper.getExperimentResultsFolderForNode(i), blobStateScpList);
		}
	}

	void resetNodes() {
		threadPoolService(sshNodes.stream()
				.<Runnable>map(node -> () -> {
					node.reset();
					node.close();
				})
				.collect(Collectors.toList()));
		sshNodes.clear();

		// Reset any test client nodes if they exist
		threadPoolService(testClientNodes.stream()
				.<Runnable>map(node -> () -> {
					node.reset();
					node.close();
				})
				.collect(Collectors.toList()));
		testClientNodes.clear();

	}

	void killJavaProcess() {
		threadPoolService(sshNodes.stream()
				.<Runnable>map(node -> () -> {
					node.killJavaProcess();
					node.close();
				})
				.collect(Collectors.toList()));
	}

	private void uploadFilesToSharepoint() {
		copyLogFileToResultsFolder();
		if (!regConfig.isUploadToSharePoint()) {
			log.info(MARKER, "Uploading to SharePoint is off, skipping");
			return;
		}
		SharePointManager spm = new SharePointManager();
		spm.login();
		try (Stream<Path> walkerPath = Files.walk(
				Paths.get(experimentLocalFileHelper.getExperimentFolder()))) {
			final List<String> foundFiles = walkerPath.filter(Files::isRegularFile).map(x -> x.toString()).collect(
					Collectors.toList());
			for (final String file : foundFiles) {
				final File currentFile = new File(file);
				//TODO get rid of 500000 and replace wit static final, split zip at 50,000 as well
/*				if(currentFile.isFile() && currentFile.getTotalSpace() > 500000){
					Path zippedFile = Paths.get(new File(currentFile.getPath() + ".zip").toURI());
					CompressResults zipper = new CompressResults(zippedFile);
					zipper.bundleFile(Paths.get(currentFile.toURI()));
					currentFile = zippedFile.toFile();
				} */
				final String path = getResultsFolder(experimentTime,
						testConfig.getName()) + file.substring(file.indexOf("results/", 1) + 8);
				path.replace("//", "/");
				log.info(MARKER, "uploadFile({},{})", path, currentFile.getName());
				spm.uploadFile(path, currentFile);
			}
		} catch (IOException e) {
			log.error(ERROR, "unable to walk experiments results", e);
		}

	}

	private void copyLogFileToResultsFolder() {
		//TODO: change regression.log into static const in RegressionUtilites
		String logFile = "regression.log";
		/*get the name of the file logger is writing to */
		org.apache.logging.log4j.core.Logger loggerImpl = (org.apache.logging.log4j.core.Logger) log;
		final Collection<Appender> logAppeneders = ((org.apache.logging.log4j.core.Logger) log).getAppenders().values();
		for (final Appender appender : logAppeneders) {
			if (appender instanceof FileAppender) {
				logFile = (((FileAppender) appender).getFileName());
			} else if (appender instanceof RandomAccessFileAppender) {
				logFile = (((RandomAccessFileAppender) appender).getFileName());
			} else if (appender instanceof RollingFileAppender) {
				logFile = (((RollingFileAppender) appender).getFileName());
			} else if (appender instanceof MemoryMappedFileAppender) {
				logFile = (((MemoryMappedFileAppender) appender).getFileName());
			} else if (appender instanceof RollingRandomAccessFileAppender) {
				logFile = (((RollingRandomAccessFileAppender) appender).getFileName());
			}
		}

		final Path logFileSource = Paths.get(logFile);
		/*TODO: change regession.log into static const in RegressionUtilities */
		final Path logFileDestination = Paths.get(
				experimentLocalFileHelper.getExperimentFolder() + "regression.log");
		if (git != null) {
			if (!new File(git.getGitLog()).exists())
				new File(git.getGitLog()).mkdirs();
			final Path gitLogSource = Paths.get(git.getGitLog());
			final Path gitLogDestination = Paths.get(
					experimentLocalFileHelper.getExperimentFolder() + git.getGitLog());
			try {
				if (gitLogSource != null)
					Files.copy(gitLogSource, gitLogDestination);
			} catch (Exception e) {
				log.error(ERROR, "Could not copy regression log", e);
			}
		} else {
			log.error(ERROR, "gitInfo object not initialized");
		}
		try {
			Files.copy(logFileSource, logFileDestination);
		} catch (IOException e) {
			log.error(ERROR, "Could not copy regression log", e);
		}
	}

	private ArrayList<File> buildAdditionalFileList() {
		ArrayList<File> returnList = new ArrayList<>();
		testConfig.getFilesNeeded().forEach((f) -> {
			returnList.add(new File(f));
		});
		return returnList;
	}

	private void exportIPAddressFiles() {
		if (cloud == null) {
			return;
		}
		ArrayList<String> pubIPs = cloud.getPublicIPList();
		ArrayList<String> prvtIPs = cloud.getPrivateIPList();
		Path pubIPFile = Paths.get(PUBLIC_IP_ADDRESS_FILE);
		Path pvtIPFile = Paths.get(PRIVATE_IP_ADDRESS_FILE);
		try {
			Files.write(pubIPFile, pubIPs, STANDARD_CHARSET);
			Files.write(pvtIPFile, prvtIPs, STANDARD_CHARSET);
		} catch (IOException e) {
			log.error(ERROR, "unable to output public and private IP address files.", e);
		}

	}

	public void startLocalTest() {
		try {
			sendSettingFileToNodes();
			int processReturnValue = startBrowser();
			log.info(MARKER, "First Process finish with value: {}", processReturnValue);

			for (int i = 0; i < regConfig.getLocal().getNumberOfNodes(); i++) {
				scpFrom(i, (ArrayList<String>) testConfig.getResultFiles());
				log.info(MARKER, "local files copied to results.");
			}

			validateTest();

			log.info(MARKER, "restart was successful.");
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private int startBrowser() throws IOException, InterruptedException {
		String javaHome = System.getProperty("java.home");
		String javaBin = javaHome +
				File.separator + "bin" +
				File.separator + "java";
		String javaOptions = getJVMOptionsString();

		ProcessBuilder builder = new ProcessBuilder(
				javaBin, javaOptions, "-jar", "swirlds.jar");
		log.info(MARKER, "builder String: {}", builder.command().toString());

		long testDuration = testConfig.getDuration();
		log.info(MARKER, "regular test duration: {}", testDuration);
		//TODO this needs to be handled differently, so commenting out for the time being
		/*
		if (testConfig.isRestart() && !isFirstTestFinished) {
			Duration dur = getRestartDuration();
			testDuration = dur.getSeconds();
			log.info(MARKER, "restart test duration: {}", testDuration);
		}
		 */
		Process process = builder.inheritIO().start();
		if (!process.waitFor(testDuration, TimeUnit.SECONDS)) {
			process.destroyForcibly();
		}
		isFirstTestFinished = true;
		return process.exitValue();
	}

	/**
	 * Creates a strong to be used for JVM options when starting an experiment on each node.
	 * If the regression config has a specific JVMOption parameter set it will be used. If not then build the string
	 * based on the memory of the instance used.
	 * if jvm options were set specifically in the regression config those will overwrite the standard defaults
	 *
	 * @return string containing JVM options
	 */
	protected String getJVMOptionsString() {
		String javaOptions;
        /* if the individual parameters for jvm options are set create the appropriate string, if not use the default.
        If a jvm options string was given in the regression config use that instead.
         */
		if (regConfig.getJvmOptionParametersConfig() != null) {
			javaOptions = RegressionUtilities.buildParameterString(regConfig.getJvmOptionParametersConfig());
		} else {
			javaOptions = new JVMConfig(nodeMemoryProfile.getJvmMemory()).getJVMOptionsString();
			// when we don't set MetaspaceSize, it was allocated 50M
			// from GC log report, FCM2.5M test got: "Our analysis tells that GCs are triggered because
			// Metadata occupancy is reaching it's limits quite often. 4 GCs were triggered because of this reason.
			// You can consider increasing the metaspace size so that this GC activity can be minimzed."
			// when we set MetaspaceSize to be 100M, there would not such problem reported
			javaOptions += " -XX:MetaspaceSize=100M ";
			// generate gc logs if the testConfig contains MemoryLeakCheckConfig
			if (testConfig.getMemoryLeakCheckConfig() != null) {
				javaOptions += JVM_OPTIONS_GC_LOG;
			}
		}
		if (!regConfig.getJvmOptions().isEmpty()) {
			javaOptions = regConfig.getJvmOptions();
		}

		return javaOptions;
	}

	void scpFrom(int node, ArrayList<String> downloadExtensions) {
		String topLevelFolders = experimentLocalFileHelper.getExperimentResultsFolderForNode(node);
		try {
			CopyOption[] options = new CopyOption[] {
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.COPY_ATTRIBUTES
			};
			log.info(MARKER, "top level folder: {}", topLevelFolders);

			Collection<File> foundFiles = getListOfFiles(getSDKFilesToDownload(downloadExtensions),
					node);
			log.info(MARKER, "total files found:{}", foundFiles.size());
			for (File file : foundFiles) {
				Path fromFile = Paths.get(file.toURI());
				String currentLine = file.getName();
				/* remove everything before remoteExperiments and "remoteExpeirments" add in experiment folder and node
				. */
				currentLine = topLevelFolders + currentLine;

				File fileToSplit = new File(currentLine);
				Path toFile = Paths.get(fileToSplit.toURI());
				if (!fileToSplit.exists()) {

					/* has to be getParentFile().mkdirs() because if it is not JAVA will make a directory with the name
					of the file like remoteExperiments/swirlds.jar. This will cause scp to take the input as a filepath
					and not the file itself leaving the directory structure like remoteExperiments/swirlds.jar/swirlds
					.jar
					 */
					fileToSplit.getParentFile().mkdirs();

				}
				Files.copy(fromFile, toFile, options);
				log.info(MARKER, "downloading {} from node {} putting it in {}", fromFile.toString(), node,
						toFile.toString());
			}
		} catch (IOException e) {
			log.error(ERROR, "Could not download files", e);
		}
	}

	Collection<File> getListOfFiles(ArrayList<String> extension, int node) {
		final ArrayList<String> ext = ChangeExtensionToBeNodeSpecific(extension, node);
		final IOFileFilter filter;
		if (ext == null) {
			filter = TrueFileFilter.INSTANCE;
		} else {
			filter = new SuffixFileFilter(ext);
		}

		return listFiles(new File("./"), filter,
				false ? TrueFileFilter.INSTANCE : FalseFileFilter.INSTANCE);
	}

	private ArrayList<String> ChangeExtensionToBeNodeSpecific(ArrayList<String> extension, int node) {
		final ArrayList<String> returnArray = new ArrayList<>();
		for (final String ext : extension) {
			returnArray.add(ext.replace("*", Integer.toString(node)));
		}
		return returnArray;
	}

	void setupTest(TestConfig experiment) {
		this.testConfig = experiment;
		RegressionUtilities.setTestConfig(testConfig);

		setExperimentTime();
		configFile = new ConfigBuilder(regConfig, testConfig);
		settingsFile = new SettingsBuilder(testConfig);

		// set up the suites from SuiteRunner that should be run on test clients in services-regression
		if (isServicesRegression()) {
			ExperimentServicesHelper.setTestSuites(testConfig);
		}
	}

	public TestConfig getTestConfig() {
		return testConfig;
	}

	public RegressionConfig getRegConfig() {
		return regConfig;
	}

	public SettingsBuilder getSettingsFile() {
		return settingsFile;
	}

	public int getNumberOfSignedStates() {
		SSHService node0 = sshNodes.get(0);
		return node0.getNumberOfSignedStates();
	}

	/** check if all nodes generated same number of states */
	public boolean generatedSameNumberStates() {
		boolean result = true;
		SSHService node0 = sshNodes.get(0);
		int node0StateNumber = node0.getNumberOfSignedStates();
		log.info(MARKER, "Important Node 0 generated {} states", node0StateNumber);
		for (int i = 1; i < sshNodes.size(); i++) {
			int stateNumber = sshNodes.get(i).getNumberOfSignedStates();
			log.info(MARKER, "Important Node {} generated {} states", i, stateNumber);
			if (stateNumber != node0StateNumber) {
				log.error(ERROR, "Node 0 and node {} have different number of states : {} vs {}",
						0, i, node0StateNumber, stateNumber);
				result = false;
			}
		}
		return result;
	}

	/**
	 * Delete last few saved states from all nodes.
	 * The number of deleted states are random generated based on current number
	 * of saved states
	 */
	public boolean randomDeleteLastNSignedStates() {
		SSHService node0 = sshNodes.get(0);
		int savedStatesAmount = node0.getNumberOfSignedStates();
		log.info(MARKER, "Found {} saved signed state", savedStatesAmount);
		if (savedStatesAmount > 1) {
			// random generate an amount and delete such amount of signed state
			// at least leave one of the original signed state
			int randNum = ((new Random()).nextInt(savedStatesAmount - 1)) + 1;
			log.info(MARKER, "Random delete {} signed state", randNum);

			deleteLastNSignedStates(randNum);
		}
		if (savedStatesAmount == 0) {
			log.error(ERROR, "no signed state saved, cannot continue recover test");
			return false;
		}
		return true;
	}

	/**
	 * Delete last few saved signed states from disk
	 *
	 * @param deleteNumber
	 * 		number of signed state to be deleted
	 */
	public void deleteLastNSignedStates(int deleteNumber) {
		for (SSHService node : sshNodes) {
			List<SavedStatePathInfo> stateList = node.getSavedStatesDirectories();
			node.deleteLastNSignedStates(deleteNumber, stateList);
		}
	}

	/**
	 * List names of signed state directories currently on disk
	 *
	 * @param memo
	 * 		Memo string
	 */
	public void displaySignedStates(String memo) {
		for (SSHService node : sshNodes) {
			node.displaySignedStates(memo);
		}
	}

	/**
	 * Hide expected map directory
	 */
	public void backupSavedExpectedMap() {
		for (SSHService node : sshNodes) {
			node.backupSavedExpectedMap();
		}
	}

	/**
	 * Restore expected map directory
	 */
	public void restoreSavedExpectedMap() {
		for (SSHService node : sshNodes) {
			node.restoreSavedExpectedMap();
		}
	}

	public void removeRecordStreamFile() {
		for (SSHService node : sshNodes) {
			node.removeRecordStreamFile();
		}
	}

	/**
	 * Backup signed state to a temp directory
	 */
	public void backupSavedSignedState(String tempDir) {
		for (SSHService node : sshNodes) {
			node.backupSavedSignedState(tempDir);
		}
	}

	/**
	 * Restore signed state from a temp directory
	 */
	public void restoreSavedSignedState(String tempDir) {
		for (SSHService node : sshNodes) {
			node.restoreSavedSignedState(tempDir);
		}
	}

	/**
	 * Compare event files generated during recover mode whether match original ones
	 *
	 * @param eventDir
	 * @param originalDir
	 * @return
	 */
	public boolean checkRecoveredEventFiles(String eventDir, String originalDir) {
		for (SSHService node : sshNodes) {
			if (!node.checkRecoveredEventFiles(eventDir, originalDir)) {
				return false;
			}

		}
		return true;
	}


	/**
	 * check if all nodes have entered MAINTENANCE mode
	 *
	 * @param iteration
	 * 		iteration number of freeze test
	 * @param isFreezeTest
	 * 		whether current test is FreezeTest or RetartTest
	 * @return
	 */
	public boolean checkAllNodesFreeze(final int iteration, final boolean isFreezeTest) {
		//expected number of CHANGED_TO_MAINTENANCE contained in swirlds.log
		final int expectedNum = iteration + 1;
		// number of tries we can perform in each iteration on each node
		final int tries = isFreezeTest ?
				testConfig.getFreezeConfig().getRetries() :
				testConfig.getRestartConfig().getRetries();
		for (int i = 0; i < sshNodes.size(); i++) {
			SSHService node = sshNodes.get(i);
			// there are this many tries left in this iteration on this node;
			int triesLeft = tries;
			boolean frozen = false;
			while (triesLeft > 0) {
				if (node.countSpecifiedMsg(List.of(CHANGED_TO_MAINTENANCE), REMOTE_SWIRLDS_LOG) == expectedNum) {
					log.info(MARKER, "Node {} enters MAINTENANCE at iteration {}", i, iteration);
					frozen = true;
					break;
				}
				triesLeft--;
				node.printCurrentTime(i);
				log.info(MARKER, "Node {} hasn't entered MAINTENANCE at iteration {}, will retry after {} s", i,
						iteration, TestRun.FREEZE_WAIT_MILLIS);
				sleepThroughExperiment(TestRun.FREEZE_WAIT_MILLIS);
			}
			if (!frozen) {
				log.error(ERROR, "Node {} hasn't entered MAINTENANCE at iteration {} after {} retries", i, iteration,
						tries);
				return false;
			}
		}
		return true;
	}

	/**
	 * check if all nodes finish saving expectedMap if they started to save expectedMap
	 *
	 * @param iteration
	 * 		iteration number of freeze test
	 * @return
	 */
	public boolean checkAllNodesSavedExpected(final int iteration) {
		final int expectedNum = iteration + 1;
		for (int i = 0; i < sshNodes.size(); i++) {
			SSHService node = sshNodes.get(i);
			final Map<String, Integer> countMap = node.countSpecifiedMsgEach(
					List.of(PTD_SAVE_EXPECTED_MAP,
							PTD_SAVE_EXPECTED_MAP_SUCCESS,
							PTD_SAVE_EXPECTED_MAP_ERROR),
					REMOTE_SWIRLDS_LOG);
			final int startCount = countMap.getOrDefault(PTD_SAVE_EXPECTED_MAP, 0);

			if (startCount == 0) {
				continue;
			}
			final int errorCount = countMap.getOrDefault(PTD_SAVE_EXPECTED_MAP_ERROR, 0);
			final int successCount = countMap.getOrDefault(PTD_SAVE_EXPECTED_MAP_SUCCESS, 0);
			if (startCount == errorCount + successCount && startCount == expectedNum) {
				log.info(MARKER, "Node {} finished/stopped saving expectedMap at iteration {}", i, iteration);
			} else {
				log.info(MARKER, "Node {} hasn't finished saving expectedMap at iteration {}", i, iteration);
				return false;
			}
		}
		return true;
	}


	/**
	 * set app in ConfigBuilder
	 *
	 * @param app
	 */
	public void setConfigApp(final AppConfig app) {
		configFile.setApp(app);
	}

	public ExperimentLocalFileHelper getExperimentLocalFileHelper() {
		return experimentLocalFileHelper;
	}
}
