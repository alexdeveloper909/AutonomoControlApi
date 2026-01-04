package autonomo.service

import autonomo.config.JsonSupport
import autonomo.domain.DefaultDomainService
import autonomo.domain.Expense
import autonomo.domain.Invoice
import autonomo.domain.MonthSummary
import autonomo.domain.QuarterSummary
import autonomo.domain.Settings
import autonomo.domain.StatePayment
import autonomo.domain.Transfer
import autonomo.model.MonthSummariesResponse
import autonomo.model.QuarterSummariesResponse
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepository
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.util.RecordPayloadParser
import java.time.YearMonth

interface SummariesServicePort {
    fun monthSummaries(workspaceId: String, settings: Settings): MonthSummariesResponse
    fun quarterSummaries(workspaceId: String, settings: Settings): QuarterSummariesResponse
}

class SummariesService(
    private val repository: WorkspaceRecordsRepositoryPort = WorkspaceRecordsRepository()
) : SummariesServicePort {
    override fun monthSummaries(workspaceId: String, settings: Settings): MonthSummariesResponse {
        val records = loadYearRecordsByMonth(workspaceId, settings.year)
        val domainService = DefaultDomainService()
        registerRecords(domainService, records)
        val summaries: List<MonthSummary> = domainService.monthSummaries(settings)
        return MonthSummariesResponse(settings = settings, items = summaries)
    }

    override fun quarterSummaries(workspaceId: String, settings: Settings): QuarterSummariesResponse {
        val records = loadYearRecordsByQuarter(workspaceId, settings.year)
        val domainService = DefaultDomainService()
        registerRecords(domainService, records)
        val summaries: List<QuarterSummary> = domainService.quarterSummaries(settings)
        return QuarterSummariesResponse(settings = settings, items = summaries)
    }

    private fun loadYearRecordsByMonth(workspaceId: String, year: Int) =
        (1..12).flatMap { month ->
            val yearMonth = YearMonth.of(year, month)
            val workspaceMonth = "WS#$workspaceId#M#${yearMonth}"
            repository.queryByMonth(workspaceMonth, recordType = null)
        }

    private fun loadYearRecordsByQuarter(workspaceId: String, year: Int) =
        (1..4).flatMap { quarter ->
            val workspaceQuarter = "WS#$workspaceId#Q#$year-Q$quarter"
            repository.queryByQuarter(workspaceQuarter, recordType = null)
        }

    private fun registerRecords(domainService: DefaultDomainService, records: List<RecordItem>) {
        records.forEach { item ->
            val payloadNode = JsonSupport.mapper.readTree(item.payloadJson)
            when (item.recordType) {
                RecordType.INVOICE ->
                    domainService.registerInvoice(RecordPayloadParser.parse(item.recordType, payloadNode) as Invoice)
                RecordType.EXPENSE ->
                    domainService.registerExpense(RecordPayloadParser.parse(item.recordType, payloadNode) as Expense)
                RecordType.STATE_PAYMENT ->
                    domainService.recordStatePayment(RecordPayloadParser.parse(item.recordType, payloadNode) as StatePayment)
                RecordType.TRANSFER ->
                    domainService.recordTransfer(RecordPayloadParser.parse(item.recordType, payloadNode) as Transfer)
                RecordType.BUDGET -> Unit
            }
        }
    }
}
