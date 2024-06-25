/*
 * Copyright (2023) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.delta.kernel.engine;

import java.io.IOException;
import java.util.Map;

import io.delta.kernel.annotation.Evolving;
import io.delta.kernel.commit.Commit;
import io.delta.kernel.commit.CommitFailedException;
import io.delta.kernel.commit.CommitResponse;
import io.delta.kernel.commit.GetCommitsResponse;
import io.delta.kernel.commit.UpdatedActions;
import io.delta.kernel.commit.actions.AbstractMetadata;
import io.delta.kernel.commit.actions.AbstractProtocol;
import io.delta.kernel.data.Row;
import io.delta.kernel.utils.CloseableIterator;

/**
 * Provides coordinated commits related functionalities to Delta Kernel.
 *
 * @since 3.0.0
 */
@Evolving
public interface CommitCoordinatorClientHandler {

    /**
     * API to register the table represented by the given `logPath` at the provided
     * currentTableVersion with the commit coordinator this commit coordinator client represents.
     * <p>
     * This API is called when the table is being converted from a file system table to a
     * coordinated-commit table.
     * <p>
     * When a new coordinated-commit table is being created, the currentTableVersion will be -1 and
     * the upgrade commit needs to be a file system commit which will write the backfilled file
     * directly.
     *
     * @param logPath         The path to the delta log of the table that should be converted
     * @param currentVersion  The currentTableVersion is the version of the table just before
     *                        conversion. currentTableVersion + 1 represents the commit that
     *                        will do the conversion. This must be backfilled atomically.
     *                        currentTableVersion + 2 represents the first commit after conversion.
     *                        This will go through the CommitCoordinatorClient and the client is
     *                        free to choose when it wants to backfill this commit.
     * @param currentMetadata The metadata of the table at currentTableVersion
     * @param currentProtocol The protocol of the table at currentTableVersion
     * @return A map of key-value pairs which is issued by the commit coordinator to identify the
     * table. This should be stored in the table's metadata. This information needs to be
     * passed to the {@link #commit}, {@link #getCommits}, and {@link #backfillToVersion}
     * APIs to identify the table.
     */
    Map<String, String> registerTable(
            String logPath,
            long currentVersion,
            AbstractMetadata currentMetadata,
            AbstractProtocol currentProtocol);

    /**
     * API to commit the given set of actions to the table represented by logPath at the
     * given commitVersion.
     *
     * @param logPath        The path to the delta log of the table that should be committed to.
     * @param tableConf      The table configuration that was returned by the commit coordinator
     *                       client during registration.
     * @param commitVersion  The version of the commit that is being committed.
     * @param actions        The actions that need to be committed.
     * @param updatedActions The commit info and any metadata or protocol changes that are made
     *                       as part of this commit.
     * @return CommitResponse which contains the file status of the committed commit file. If the
     * commit is already backfilled, then the file status could be omitted from the response
     * and the client could retrieve the information by itself.
     */
    CommitResponse commit(
            String logPath,
            Map<String, String> tableConf,
            long commitVersion,
            CloseableIterator<Row> actions,
            UpdatedActions updatedActions) throws IOException, CommitFailedException;

    /**
     * API to get the unbackfilled commits for the table represented by the given logPath.
     * Commits older than startVersion or newer than endVersion (if given) are ignored. The
     * returned commits are contiguous and in ascending version order.
     *
     * Note that the first version returned by this API may not be equal to startVersion. This
     * happens when some versions starting from startVersion have already been backfilled and so
     * the commit coordinator may have stopped tracking them.
     *
     * The returned latestTableVersion is the maximum commit version ratified by the commit
     * coordinator. Note that returning latestTableVersion as -1 is acceptable only if the commit
     * coordinator never ratified any version, i.e. it never accepted any unbackfilled commit.
     *
     * @param tablePath The path to the delta log of the table for which the unbackfilled
     *                  commits should be retrieved.
     * @param tableConf The table configuration that was returned by the commit coordinator
     *                  during registration.
     * @param startVersion The minimum version of the commit that should be returned. Can be null.
     * @param endVersion The maximum version of the commit that should be returned. Can be null.
     * @return GetCommitsResponse which has a list of {@link Commit}s and the latestTableVersion
     *         which is tracked by {@link CommitCoordinatorClientHandler}.
     */
    GetCommitsResponse getCommits(
            String tablePath,
            Map<String, String> tableConf,
            Long startVersion,
            Long endVersion);

    /**
     * API to ask the commit coordinator client to backfill all commits up to {@code version}
     * and notify the commit coordinator.
     *
     * If this API returns successfully, that means the backfill must have been completed, although
     * the commit coordinator may not be aware of it yet.
     *
     * @param logPath The path to the delta log of the table that should be backfilled.
     * @param tableConf The table configuration that was returned by the commit coordinator
     *                  during registration.
     * @param version The version till which the commit coordinator client should backfill.
     * @param lastKnownBackfilledVersion The last known version that was backfilled before this API
     *                                   was called. If it is None or invalid, then the commit
     *                                   coordinator client should backfill from the beginning of
     *                                   the table. Can be null.
     */
    void backfillToVersion(
            String logPath,
            Map<String, String> tableConf,
            long version,
            Long lastKnownBackfilledVersion) throws IOException;

    /**
     * Determines whether this CommitCoordinatorClient is semantically equal to another
     * CommitCoordinatorClient.
     *
     * Semantic equality is determined by each CommitCoordinatorClient implementation based on
     * whether the two instances can be used interchangeably when invoking any of the
     * CommitCoordinatorClient APIs, such as {@link #commit}, {@link #getCommits}, etc. For example,
     * both instances might be pointing to the same underlying endpoint.
     */
    Boolean semanticEquals(CommitCoordinatorClientHandler other);
}
