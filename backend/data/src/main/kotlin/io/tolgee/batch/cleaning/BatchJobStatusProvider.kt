package io.tolgee.batch.cleaning

import io.tolgee.model.batch.BatchJobChunkExecutionStatus
import io.tolgee.model.batch.BatchJobStatus
import org.springframework.stereotype.Component

@Component
class BatchJobStatusProvider {
  fun getNewStatus(it: Collection<BatchJobChunkExecutionStatus>) =
    when {
      it.contains(BatchJobChunkExecutionStatus.FAILED) -> BatchJobStatus.FAILED
      it.contains(BatchJobChunkExecutionStatus.CANCELLED) -> BatchJobStatus.CANCELLED
      else -> BatchJobStatus.SUCCESS
    }
}
