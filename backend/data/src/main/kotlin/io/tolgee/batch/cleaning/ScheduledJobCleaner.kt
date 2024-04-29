package io.tolgee.batch.cleaning

import io.tolgee.batch.BatchJobProjectLockingManager
import io.tolgee.batch.BatchJobService
import io.tolgee.batch.CachingBatchJobService
import io.tolgee.batch.state.BatchJobStateProvider
import io.tolgee.component.CurrentDateProvider
import io.tolgee.model.batch.BatchJobChunkExecutionStatus
import io.tolgee.model.batch.BatchJobStatus
import io.tolgee.util.Logging
import io.tolgee.util.addSeconds
import io.tolgee.util.logger
import io.tolgee.util.runSentryCatching
import jakarta.persistence.EntityManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ScheduledJobCleaner(
  private val batchJobService: BatchJobService,
  private val lockingManager: BatchJobProjectLockingManager,
  private val currentDateProvider: CurrentDateProvider,
  private val batchJobStateProvider: BatchJobStateProvider,
  private val entityManager: EntityManager,
  private val cachingBatchJobService: CachingBatchJobService,
  private val batchJobStatusProvider: BatchJobStatusProvider,
) : Logging {
  /**
   * Sometimes it doesn't unlock the job for project (for some reason)
   * For that reason, we have this scheduled task that unlocks all completed jobs
   */
  @Scheduled(fixedDelayString = """${'$'}{tolgee.batch.scheduled-unlock-job-delay:10000}""")
  fun cleanup() {
    runSentryCatching {
      handleCompletedJobsCacheState()
      handleStuckJobs()
    }
  }

  private fun handleStuckJobs() {
    batchJobService.getStuckJobIds(batchJobStateProvider.getCachedJobIds()).forEach {
      logger.warn("Removing stuck job state it using scheduled task")
      batchJobStateProvider.removeJobState(it)
    }
  }

  private fun handleCompletedJobsCacheState() {
    val lockedJobIds = lockingManager.getLockedJobIds() + batchJobStateProvider.getCachedJobIds()
    batchJobService.getJobsCompletedBefore(lockedJobIds, currentDateProvider.date.addSeconds(-10))
      .forEach {
        unlockAndRemoveState(it.project.id, it.id)
      }
  }

  /**
   * Sometimes, (no-one knows why) Tolgee completes all the chunk executions
   * (they're not in RUNNING or PENDING state), but
   * the status of the batch job is still running or pending. This method handles this by updating the batch job state.
   *
   * The right way to fix this would be to find the cause, why the last completed execution doesn't update the master
   * job state, but it's extremely tricky, because it happens in really limited number of times.
   */
  @Scheduled(fixedDelayString = """${'$'}{tolgee.batch.scheduled-handle-stuck-job-delay:300000}""")
  @Transactional
  fun handleStuckCompletedJobs() {
    val jobs = getStuckCompletedJobs()
    jobs.forEach {
      val newStatus = batchJobStatusProvider.getNewStatus(it.statuses)
      updateStatus(it, newStatus)
      unlockAndRemoveState(projectId = it.projectId, it.id)
    }
  }

  private fun updateStatus(
    stuckJob: StuckCompletedJob,
    newStatus: BatchJobStatus,
  ) {
    entityManager.createNativeQuery("update tolgee_batch_job set status = :newStatus where id = :id")
      .setParameter("newStatus", newStatus.name)
      .setParameter("id", stuckJob.id)
      .executeUpdate()
  }

  private fun getStuckCompletedJobs(): List<StuckCompletedJob> {
    val chunkIncompleteStatuses = BatchJobChunkExecutionStatus.entries.filter { !it.completed }.map { it.name }
    val jobIncompleteStatuses = BatchJobStatus.entries.filter { !it.completed }.map { it.name }

    val data =
      entityManager.createNativeQuery(
        """
        select tbj.id as id, tbj.project_id as projectId, tbjce2.status as status
        from tolgee_batch_job tbj
                 left join tolgee_batch_job_chunk_execution tbjce
                           on tbj.id = tbjce.batch_job_id and tbjce.status in :chunkIncompleteStatuses
                 left join tolgee_batch_job_chunk_execution tbjce2 
                           on tbj.id = tbjce2.batch_job_id
        where tbj.status in :jobIncompleteStatuses
          and tbjce.id is null
        group by  tbj.id, tbj.project_id, tbjce2.status
      """,
        Array<Any>::class.java,
      )
        .setParameter("chunkIncompleteStatuses", chunkIncompleteStatuses)
        .setParameter("jobIncompleteStatuses", jobIncompleteStatuses)
        .resultList as List<Array<Any>>

    return data.groupBy { it[0] }.map { rawDataList ->
      val statuses =
        rawDataList.value.mapNotNull { rawData ->
          val string = rawData[2] as String?
          string?.let { BatchJobChunkExecutionStatus.valueOf(it) }
        }
      StuckCompletedJob(rawDataList.key as Long, rawDataList.value.first()[1] as Long?, statuses)
    }
  }

  private fun unlockAndRemoveState(
    projectId: Long?,
    id: Long,
  ) {
    projectId?.let {
      logger.warn("Unlocking completed job $id using scheduled task")
      lockingManager.unlockJobForProject(it, id)
    }
    logger.warn("Removing completed job state $id using scheduled task")
    batchJobStateProvider.removeJobState(id)
    cachingBatchJobService.evictJobCache(id)
  }
}
