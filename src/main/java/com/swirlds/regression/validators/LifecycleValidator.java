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

import com.swirlds.fcmap.test.lifecycle.ExpectedValue;
import com.swirlds.fcmap.test.lifecycle.LifecycleStatus;
import com.swirlds.fcmap.test.lifecycle.SaveExpectedMapHandler;
import com.swirlds.fcmap.test.lifecycle.TransactionState;
import com.swirlds.fcmap.test.lifecycle.TransactionType;
import com.swirlds.fcmap.test.pta.MapKey;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.swirlds.fcmap.test.lifecycle.TransactionState.HANDLED;
import static com.swirlds.fcmap.test.lifecycle.TransactionState.SUBMISSION_FAILED;
import static com.swirlds.fcmap.test.lifecycle.TransactionType.Delete;
import static com.swirlds.fcmap.test.lifecycle.TransactionType.Expire;


/**
 * Validator to validate lifecycle of all entities in ExpectedMap
 */
public class LifecycleValidator extends Validator {
	/**
	 * key: nodeId; value: path of ExpectedMap
	 */
	private Map<Integer, String> expectedMapPaths;

	private boolean isValid;
	private boolean isValidated;
	public static final String EXPECTED_MAP_ZIP = "ExpectedMap.json.gz";

	public static final String HANDLE_REJECTED_ERROR = "ExpectedValue of Key %s on node %d has the " +
			"latestHandledStatus TransactionState as HANDLE_REJECTED. But, the HistoryHandledStatus " +
			"is not Deleted/Expired. It is %s";
	public static final String PERFORM_ON_NON_EXISTING_ERROR = "LatestHandledStatus of key %s on " +
			"node %d is HANDLE_REJECTED. But, the historyHandledStatus is null. An operation %s is " +
			"performed on non existing entity when performOnNonExistingEntities is false";
	public static final String MISSING_KEYS_ERROR = "KeySet of the expectedMap of node %d doesn't match with " +
			"expectedMap of node %d. " +
			"Missing keys in node %d : %s, MissingKeys in node 0 : %s";
	public static final String FIELD_MISMATCH_ERROR = "Entity: %s has field %s mismatched. node0: %s; node%d: %s";

	public static final String NULL_LATEST_HANDLED_STATUS_ERROR = "latestHandleStatus of one of the expectedValues is" +
			" " +
			"null. " +
			"Node 0 : %s , Node %d : %s ";

	public LifecycleValidator(final Map<Integer, String> expectedMapPaths) {
		this.expectedMapPaths = expectedMapPaths;
		isValid = false;
		isValidated = false;
	}

	@Override
	public List<String> getErrorMessages() {
		return errorMessages;
	}

	/**
	 * Validate the expectedMaps downloaded in the results folder after an experiment is completed
	 */
	@Override
	public void validate() {
		validateExpectedMaps();
	}

	/**
	 * Check if the experiment completed successfully
	 *
	 * @return Boolean that signifies there are no error messages while validating expectedMaps from all nodes
	 */
	@Override
	public boolean isValid() {
		return isValid && isValidated;
	}

	/**
	 * ExpectedMaps are valid if there are no error messages recorded while
	 * expectedMaps from all nodes
	 */
	private void validateExpectedMaps() {
		if (expectedMapPaths == null || expectedMapPaths.size() == 0) {
			addError("ExpectedMap doesn't exist for validation");
			isValid = false;
			isValidated = true;
			return;
		}
		Map<MapKey, ExpectedValue> baselineMap = SaveExpectedMapHandler.deserialize(expectedMapPaths.get(0));
		for (int nodeId = 1; nodeId < expectedMapPaths.size(); nodeId++) {
			Map<MapKey, ExpectedValue> mapToCompare = SaveExpectedMapHandler.deserialize(expectedMapPaths.get(nodeId));

			if (!baselineMap.keySet().equals(mapToCompare.keySet())) {
				checkMissingKeys(baselineMap, mapToCompare, nodeId);
			}

			for (MapKey key : baselineMap.keySet()) {
				if (mapToCompare.containsKey(key)) {
					ExpectedValue baseValue = baselineMap.get(key);
					ExpectedValue compareValue = mapToCompare.get(key);
					if (!baseValue.equals(compareValue)) {
						compareValues(key, baseValue, compareValue, nodeId);
					} else {
						checkHandleRejectedStatus(key, baseValue, compareValue, nodeId);
					}
					if (baseValue.isErrored() && compareValue.isErrored()) {
						checkErrorCause(key, baseValue, 0);
						checkErrorCause(key, compareValue, nodeId);
					}
				}
			}
		}
		addInfo("LifecycleValidator validated ExpectedMaps of " + expectedMapPaths.size() + " nodes");
		if (errorMessages.size() == 0) {
			isValid = true;
			addInfo("Validator has no exceptions");
		}
		isValidated = true;
	}

	/**
	 * check if there are any entities that have latestHandledStatus as HANDLE_REJECTED
	 *
	 * @param key
	 * 		key of the entity
	 * @param baseValue
	 * 		expectedValue of entity in first node
	 * @param compareValue
	 * 		expectedValue of entity in other node that is being compared
	 * @param nodeNum
	 * 		Node number of the node on which entities are being compared
	 */
	public void checkHandleRejectedStatus(MapKey key, ExpectedValue baseValue, ExpectedValue compareValue,
			int nodeNum) {
		LifecycleStatus baseLifecycle = baseValue.getLatestHandledStatus();
		LifecycleStatus compareLifecycle = compareValue.getLatestHandledStatus();
		LifecycleStatus baseHistory = baseValue.getHistoryHandledStatus();
		LifecycleStatus compareHistory = compareValue.getHistoryHandledStatus();

		if ((baseLifecycle == null && compareLifecycle == null) ||
				(baseLifecycle.getTransactionState() == null && compareLifecycle.getTransactionState() == null))
			return;

		if (baseLifecycle == null || compareLifecycle == null) {
			addError(String.format(NULL_LATEST_HANDLED_STATUS_ERROR, baseLifecycle, nodeNum, compareLifecycle));
			return;
		}

		if ((baseLifecycle.getTransactionState().equals(TransactionState.HANDLE_REJECTED)))
			checkHistory(key, baseLifecycle.getTransactionType(), baseHistory, 0);

		if ((compareLifecycle.getTransactionState().equals(TransactionState.HANDLE_REJECTED)))
			checkHistory(key, compareLifecycle.getTransactionType(), compareHistory, nodeNum);
	}

	/**
	 * Check the historyHandleStatus of an entity when the latestHandledStatus is HANDLE_REJECTED.
	 *
	 * @param key
	 * 		key of the entity
	 * @param handledTransactionType
	 * 		latestHandledTransaction type on the entity
	 * @param history
	 * 		historyHandleStatus of the entity
	 * @param nodeNum
	 * 		Node number of the node on which entities are being compared
	 */
	private void checkHistory(MapKey key, TransactionType handledTransactionType, LifecycleStatus history,
			int nodeNum) {
		if (history == null) {
			addError(String.format(PERFORM_ON_NON_EXISTING_ERROR, key, nodeNum, handledTransactionType));
			return;
		}
		TransactionType historyType = history.getTransactionType();
		if (historyType == null) {
			addError(String.format(HANDLE_REJECTED_ERROR, key, nodeNum, null));
			return;
		} else if (!(historyType.equals(Delete) ||
				historyType.equals(Expire))) {
			addError(String.format(HANDLE_REJECTED_ERROR, key, nodeNum, historyType));
		}
	}

	/**
	 * If the KeySet size of maps differ logs error with the missing keys
	 *
	 * @param baselineMap
	 * 		expectedMap of firstNode
	 * @param mapToCompare
	 * 		expectedMap on the node that is being compared
	 * @param nodeNum
	 * 		Node number of the node on which entities are being compared
	 */
	public void checkMissingKeys(Map<MapKey, ExpectedValue> baselineMap, Map<MapKey, ExpectedValue> mapToCompare, int nodeNum) {
		Set<MapKey> baseKeySet = baselineMap.keySet();
		Set<MapKey> compareKeySet = mapToCompare.keySet();

		Set<MapKey> missingKeysInCompare = new HashSet<>();
		Set<MapKey> missingKeysInBase = new HashSet<>();

		missingKeysInBase.addAll(compareKeySet);
		missingKeysInCompare.addAll(baseKeySet);

		missingKeysInBase.removeAll(baseKeySet);
		missingKeysInCompare.removeAll(compareKeySet);

		missingKeysInBase = checkRemoved(mapToCompare, missingKeysInBase);
		missingKeysInCompare = checkRemoved(baselineMap, missingKeysInCompare);

		if (missingKeysInBase.size() > 0 || missingKeysInCompare.size() > 0) {
			addError(String.format(MISSING_KEYS_ERROR, nodeNum, 0, nodeNum, missingKeysInCompare, missingKeysInBase));
		}
	}

	/**
	 * check if the missing keys are Deleted or Expired in the given expectedMap.
	 * If so, ignore them as missing Keys.
	 *
	 * @param expectedMap
	 * @param missingKeys
	 * @return Set of missing keys other than expired or deleted ones
	 */
	private Set<MapKey> checkRemoved(final Map<MapKey, ExpectedValue> expectedMap, final Set<MapKey> missingKeys) {
		Set<MapKey> missingKeysSet = new HashSet<>();
		for (MapKey key : missingKeys) {
			LifecycleStatus latestHandleStatus = expectedMap.get(key).getLatestHandledStatus();
			if (latestHandleStatus != null && latestHandleStatus.getTransactionType() != null &&
					(!latestHandleStatus.getTransactionType().equals(Delete) &&
							!latestHandleStatus.getTransactionType().equals(Expire))) {
				missingKeysSet.add(key);
			}
		}
		return missingKeysSet;
	}

	/**
	 * Build a String message to be used in compareValues()
	 *
	 * @param key
	 * 		key of the mismatched entity
	 * @param base
	 * 		First object to be compared
	 * @param other
	 * 		Other object to be compared
	 * @param nodeNum
	 * 		Node number of the node on which entities are being compared
	 * @param fieldName
	 * 		Field that is mismatched in the entity
	 * @return
	 */
	public static String buildFieldMissMatchMsg(final MapKey key, final Object base,
			final Object other, final int nodeNum, final String fieldName) {
		return String.format(FIELD_MISMATCH_ERROR, key, fieldName, base, nodeNum, other);
	}

	/**
	 * If two ExpectedValues doesn't match checks all the fields of expectedValues
	 * and logs which fields mismatch
	 *
	 * @param key
	 * 		key of the entity
	 * @param ev1
	 * 		ExpectedValue to be compared
	 * @param ev2
	 * 		ExpectedValue of entity on other node to be compared
	 * @param nodeNum
	 * 		Node number of the node on which entities are being compared
	 */
	public void compareValues(MapKey key, ExpectedValue ev1, ExpectedValue ev2, int nodeNum) {
		if (!Objects.equals(ev1.getEntityType(), ev2.getEntityType())) {
			addError(buildFieldMissMatchMsg(key, ev1.getEntityType(),
					ev2.getEntityType(), nodeNum, "EntityType"));
		}

		if (!Objects.equals(ev1.isErrored(), ev2.isErrored())) {
			addError(buildFieldMissMatchMsg(key, ev1.isErrored(),
					ev2.isErrored(), nodeNum, "isErrored"));
		}

		if (!Objects.equals(ev1.getHash(), ev2.getHash())) {
			addError(buildFieldMissMatchMsg(key, ev1.getHash(),
					ev2.getHash(), nodeNum, "getHash"));
		}

		if (!checkLatestHandledStatus(ev1.getLatestHandledStatus(), ev2.getLatestHandledStatus())) {
			addError(buildFieldMissMatchMsg(key, ev1.getLatestHandledStatus(),
					ev2.getLatestHandledStatus(), nodeNum, "latestHandledStatus"));
		}

		if (!checkHistoryHandledStatus(ev1.getLatestHandledStatus(), ev2.getLatestHandledStatus(),
				ev1.getHistoryHandledStatus(), ev2.getHistoryHandledStatus())) {
			addError(buildFieldMissMatchMsg(key, ev1.getHistoryHandledStatus(),
					ev2.getHistoryHandledStatus(), nodeNum, "historyHandledStatus"));
		}
	}

	/**
	 * If isErrored flag is set to true on an ExpectedValue in expectedMap, it means some error
	 * occurred during the experiment. Checks the causes for error.
	 *
	 * @param key
	 * 		key of the entity
	 * @param ev2
	 * 		ExpectedValue of the entity
	 * @param nodeNum
	 * 		Node number of the node on which entities are being compared
	 */
	public void checkErrorCause(MapKey key, ExpectedValue ev2, int nodeNum) {
		LifecycleStatus latestHandleStatus = ev2.getLatestHandledStatus();
		LifecycleStatus latestSubmitStatus = ev2.getLatestSubmitStatus();

		if (latestSubmitStatus != null && latestSubmitStatus.getTransactionState() != null &&
				latestSubmitStatus.getTransactionState().equals(SUBMISSION_FAILED)) {
			addError("Operation " + latestSubmitStatus.getTransactionType() +
					" failed to get successfully submitted on node " + nodeNum + " for entity " + key);
		}
		if (latestHandleStatus == null || latestHandleStatus.getTransactionState() == null)
			return;

		switch (latestHandleStatus.getTransactionState()) {
			case INVALID_SIG:
				addError(String.format("Signature is not valid for Entity %s while performing operation " +
						"%s on Node %d", key, latestHandleStatus.getTransactionType(), nodeNum));
				break;
			case HANDLE_FAILED:
				addError(String.format("Entity %s on Node %d has Error. Please look at the log for " +
						"more details", key, nodeNum));
				break;
			case HANDLE_REJECTED:
				addError(String.format("Operation %s on Entity %s in Node %d failed as entity is Deleted " +
						"and PerformOnDeleted is false or entity doesn't exist and performOnNonExistingEntities " +
						"is false", latestHandleStatus.getTransactionType(), key, nodeNum));
				break;
			case HANDLE_ENTITY_TYPE_MISMATCH:
				addError(String.format("Operation %s failed as it is performed on wrong entity type %s",
						latestHandleStatus.getTransactionType(), ev2.getEntityType()));
				break;
			default:
				addError(String.format("Something went wrong and entity %s on Node %d has Error." +
						"Please look at the log for more details", key, nodeNum));
		}
	}

	/**
	 * check if two latest handled LifecycleStatus match.
	 * two handled LifecycleStatus match in one of the following cases:
	 * (1) two LifecycleStatus equal;
	 * (2) two LifecycleStatus both have `Rebuild` TransactionType
	 * (3) one LifecycleStatus is (Rebuild, RESTART_ORIGIN/RECONNECT_ORIGIN),
	 * and the other is (TransactionType except Delete and Expire, HANDLED);
	 *
	 * @param status1
	 * 		LatestHandledStatus of first entity in comparison
	 * @param status2
	 * 		LatestHandledStatus of second entity in comparison
	 * @return return true if check passes; return false otherwise
	 */
	boolean checkLatestHandledStatus(final LifecycleStatus status1, final LifecycleStatus status2) {
		if (Objects.equals(status1, status2)) {
			return true;
		}

		if (status1 == null || status2 == null) {
			return false;
		}

		boolean rebuilt1 = isRebuilt(status1);
		boolean rebuilt2 = isRebuilt(status2);

		if (rebuilt1 && rebuilt2) {
			return true;
		}
		// if they are not equal, and none of them is rebuilt, return false
		if (!rebuilt1 && !rebuilt2) {
			return false;
		}

		if (rebuilt1) {
			return handledNotRemoved(status2);
		}

		// here rebuilt2 should be true
		return handledNotRemoved(status1);
	}

	/**
	 * Given two LatestHandledStatus match;
	 * check if two HistoryHandledStatus matches;
	 *
	 * @param latest1
	 * 		LatestHandledStatus of first entity in comparison
	 * @param latest2
	 * 		LatestHandledStatus of second entity in comparison
	 * @param history1
	 * 		HistoryHandledStatus of first entity in comparison
	 * @param history2
	 * 		HistoryHandledStatus of second entity in comparison
	 * @return true if check pass else returns false
	 */
	boolean checkHistoryHandledStatus(final LifecycleStatus latest1, final LifecycleStatus latest2,
			final LifecycleStatus history1, final LifecycleStatus history2) {
		if (Objects.equals(history1, history2)) {
			return true;
		}

		// if an entity is rebuilt during reconnect/restart, its history could be null
		// of if an entity's history is rebuilt;
		// when another history is handledNotRemoved, we consider the two match
		if (history1 == null && isRebuilt(latest1) || isRebuilt(history1)) {
			return handledNotRemoved(history2);
		}

		if (history2 == null && isRebuilt(latest2) || isRebuilt(history2)) {
			return handledNotRemoved(history1);
		}

		return false;
	}

	/**
	 * if TransactionType of LifecycleStatus is Rebuild
	 *
	 * @param lifecycleStatus
	 * 		LatestHandledStatus of the entity
	 * @return true if the transactionType is Rebuild
	 */
	boolean isRebuilt(final LifecycleStatus lifecycleStatus) {
		return lifecycleStatus != null && lifecycleStatus.getTransactionType() != null &&
				lifecycleStatus.getTransactionType() == TransactionType.Rebuild;
	}

	/**
	 * if the LifecycleStatus is HANDLED and the entity is not removed,
	 *
	 * @param lifecycleStatus
	 * 		LatestHandledStatus of the entity
	 * @return true if the entity is not Deleted or expired
	 */
	boolean handledNotRemoved(final LifecycleStatus lifecycleStatus) {
		return lifecycleStatus != null &&
				lifecycleStatus.getTransactionType() != Delete &&
				lifecycleStatus.getTransactionType() != Expire
				&& lifecycleStatus.getTransactionState() == HANDLED;
	}
}