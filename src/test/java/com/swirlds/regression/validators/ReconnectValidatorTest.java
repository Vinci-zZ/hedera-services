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

package com.swirlds.regression.validators;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swirlds.common.logging.LogMarkerInfo;
import com.swirlds.regression.jsonConfigs.TestConfig;
import com.swirlds.regression.jsonConfigs.runTypeConfigs.ReconnectConfig;
import com.swirlds.regression.logs.LogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.swirlds.regression.validators.RestartValidatorTest.loadNodeData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconnectValidatorTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"logs/reconnectFCM/2_killNode",
			"logs/reconnectFCM/3_disable_enable_network"
	})
	void validateReconnectLogs(String testDir) throws IOException {
		System.out.println("Dir: " + testDir);
		List<NodeData> nodeData = loadNodeData(testDir);
		NodeValidator validator = new ReconnectValidator(nodeData, null);
		validator.validate();
		for (String msg : validator.getInfoMessages()) {
			System.out.println("INFO: " + msg);
		}
		for (String msg : validator.getErrorMessages()) {
			System.out.println("ERROR: " + msg);
		}
		assertEquals(true, validator.isValid());
	}

	@Test
	public void csvValidatorForNodeKillReconnect() throws IOException {
		List<NodeData> nodeData = loadNodeData("logs/PTD-NodeKillReconnect");
		NodeValidator validator = new ReconnectValidator(nodeData, null);
		validator.validate();
		for (String msg : validator.getInfoMessages()) {
			System.out.println(msg);
		}
		for (String msg : validator.getErrorMessages()) {
			System.out.println(msg);
		}
		assertEquals(true, validator.isValid());
		assertEquals(0, validator.getErrorMessages().size());
	}

	/**
	 * node0 and node3 miss swirlds.log file
	 */
	@Test
	public void nodeLogIsNullTest() {
		List<NodeData> nodeData = loadNodeData("logs/PTD-MissLog03");
		ReconnectValidator validator = new ReconnectValidator(nodeData, null);
		assertFalse(validator.nodeLogIsNull(nodeData.get(1).getLogReader(), 1));
		assertFalse(validator.nodeLogIsNull(nodeData.get(2).getLogReader(), 2));

		assertTrue(validator.nodeLogIsNull(nodeData.get(0).getLogReader(), 0));
		assertTrue(validator.nodeLogIsNull(nodeData.get(3).getLogReader(), 3));

		assertEquals(false, validator.isValid());
		assertEquals(2, validator.getErrorMessages().size());
	}

	/**
	 * node0 and node2 miss csv file
	 */
	@Test
	public void nodeCsvIsNullTest() {
		List<NodeData> nodeData = loadNodeData("logs/PTD-MissCsv02");
		ReconnectValidator validator = new ReconnectValidator(nodeData, null);
		assertFalse(validator.nodeCsvIsNull(nodeData.get(1).getCsvReader(), 1));
		assertFalse(validator.nodeCsvIsNull(nodeData.get(3).getCsvReader(), 3));

		assertTrue(validator.nodeCsvIsNull(nodeData.get(0).getCsvReader(), 0));
		assertTrue(validator.nodeCsvIsNull(nodeData.get(2).getCsvReader(), 2));

		assertEquals(false, validator.isValid());
		assertEquals(2, validator.getErrorMessages().size());
	}


	@Test
	public void isAcceptableTest() {
		final int nodesNum = 4;
		final int firstId = 0;
		final int lastId = nodesNum - 1;

		ReconnectValidator validator = dummyReconnectValidator(nodesNum);

		LogEntry reconnectAcceptable = new LogEntry(Instant.now(),
				LogMarkerInfo.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT,
				0, "thread",
				"Exceptions acceptable at reconnect",
				true);
		assertTrue(validator.isAcceptable(reconnectAcceptable, firstId));
		assertTrue(validator.isAcceptable(reconnectAcceptable, lastId));

		LogEntry reconnectNodeAcceptable = new LogEntry(Instant.now(),
				LogMarkerInfo.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT_NODE,
				0, "thread",
				"Exceptions acceptable for reconnect node",
				true);
		// this exception should only be acceptable for the reconnect node
		assertFalse(validator.isAcceptable(reconnectNodeAcceptable, firstId));
		assertTrue(validator.isAcceptable(reconnectNodeAcceptable, lastId));
	}

	/**
	 * For KillNetwork reconnect test we can ignore the "Error during receiving signedState"
	 * if it happens within 30 seconds of platform becoming ACTIVE. Test to check if it is not thrown as error
	 */
	@Test
	public void checkErrorKillNetwork(){
		boolean isError = false;
		List<NodeData> nodeData = loadNodeData("logs/PTD-SignedState-Error-KillNetwork");
		assertEquals(4, nodeData.size());
		try {
			Path testConfigFileLocation = Paths.get("configs/testReconnectBlobCfg.json");
			byte[] jsonData = Files.readAllBytes(testConfigFileLocation);
			ObjectMapper objectMapper = new ObjectMapper().configure(JsonParser.Feature.ALLOW_COMMENTS, true);
			TestConfig testConfig = objectMapper.readValue(jsonData, TestConfig.class);
			assertTrue(testConfig.getReconnectConfig().isKillNetworkReconnect());
			ReconnectValidator validator = new ReconnectValidator(nodeData, testConfig);
			validator.validate();
			validator.getErrorMessages().stream().forEach(e -> System.out.println(e));
			assertEquals(0, validator.getErrorMessages().size());
			assertTrue(validator.getInfoMessages().contains("Node 3 error during receiving SignedState. It can be ignored, " +
					"as it occurred within 30 seconds of platform becoming ACTIVE"));
		}catch(IOException e){
			isError = true;
		}

		assertEquals(false, isError);
	}

	/**
	 * For KillNode reconnect test we can't ignore the "Error during receiving signedState".
	 * Test to check that log message is thrown as error.
	 */
	@Test
	public void checkErrorKillNode(){
		boolean isError = false;
		List<NodeData> nodeData = loadNodeData("logs/PTD-SignedState-Error-KillNetwork");
		assertEquals(4, nodeData.size());
		try {
			Path testConfigFileLocation = Paths.get("configs/testReconnectBlobCfg.json");
			byte[] jsonData = Files.readAllBytes(testConfigFileLocation);
			ObjectMapper objectMapper = new ObjectMapper().configure(JsonParser.Feature.ALLOW_COMMENTS, true);
			TestConfig testConfig = objectMapper.readValue(jsonData, TestConfig.class);
			testConfig.getReconnectConfig().setKillNetworkReconnect(false);
			assertFalse(testConfig.getReconnectConfig().isKillNetworkReconnect());
			ReconnectValidator validator = new ReconnectValidator(nodeData, testConfig);
			validator.validate();
			validator.getErrorMessages().stream().forEach(e -> System.out.println(e));
			assertEquals(2, validator.getErrorMessages().size());
			assertTrue(validator.getErrorMessages().get(0).equals("Node 3 error during receiving SignedState"));
			assertTrue(validator.getErrorMessages().get(1).equals("Node 3 has 1 unexpected errors!"));
		}catch(IOException e){
			isError = true;
		}

		assertEquals(false, isError);
	}

	/**
	 * get a dummy ReconnectValidator with NodeData's size be defined
	 * @return
	 */
	ReconnectValidator dummyReconnectValidator(final int nodesNum) {
		List<NodeData> nodeData = new ArrayList<>();
		for (int i = 0; i < nodesNum; i++) {
			nodeData.add(new NodeData(null, null));
		}

		return new ReconnectValidator(nodeData, null);
	}

}