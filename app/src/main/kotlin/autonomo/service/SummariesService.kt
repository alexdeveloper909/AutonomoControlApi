package autonomo.service

import autonomo.config.JsonSupport
import autonomo.domain.DefaultDomainService
import autonomo.domain.Expense
import autonomo.domain.Invoice
import autonomo.domain.MonthSummary
import autonomo.domain.QuarterSummary
import autonomo.domain.RentaEstimate
import autonomo.domain.RentaPlanner
import autonomo.domain.Settings
import autonomo.domain.StatePayment
import autonomo.domain.Transfer
import autonomo.model.MonthSummariesResponse
import autonomo.model.QuarterSummariesResponse
import autonomo.model.RentaSummaryResponse
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepository
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.util.RecordPayloadParser
import java.time.LocalDate
import java.time.YearMonth

interface SummariesServicePort {
    fun monthSummaries(workspaceId: String, settings: Settings): MonthSummariesResponse
    fun quarterSummaries(workspaceId: String, settings: Settings): QuarterSummariesResponse
    fun rentaSummary(workspaceId: String, settings: Settings): RentaSummaryResponse
}

class SummariesService(
    private val repository: WorkspaceRecordsRepositoryPort = WorkspaceRecordsRepository()
) : SummariesServicePort {
    override fun monthSummaries(workspaceId: String, settings: Settings): MonthSummariesResponse {
        val records = loadYearRecordsByMonth(workspaceId, settings.year)
        val parsed = parseRecords(records)
        val domainService = DefaultDomainService()
        registerParsed(domainService, parsed)
        val summaries: List<MonthSummary> = domainService.monthSummaries(settings)
        return MonthSummariesResponse(settings = settings, items = summaries)
    }

    override fun quarterSummaries(workspaceId: String, settings: Settings): QuarterSummariesResponse {
        val records = loadYearRecordsByQuarter(workspaceId, settings.year)
        val parsed = parseRecords(records)
        val domainService = DefaultDomainService()
        registerParsed(domainService, parsed)
        val summaries: List<QuarterSummary> = domainService.quarterSummaries(settings)
        return QuarterSummariesResponse(settings = settings, items = summaries)
    }

    override fun rentaSummary(workspaceId: String, settings: Settings): RentaSummaryResponse {
        val taxYear = settings.rentaPlanning?.taxYear ?: settings.year
        val records = loadYearRecordsByMonth(workspaceId, taxYear)
        val parsed = parseRecords(records)
        val renta: RentaEstimate? = RentaPlanner.estimate(
            settings = settings,
            invoices = parsed.invoices,
            expenses = parsed.expenses,
            payments = parsed.payments,
            asOfDate = LocalDate.now()
        )
        return RentaSummaryResponse(settings = settings, renta = renta)
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

    private data class ParsedRecords(
        val invoices: List<Invoice>,
        val expenses: List<Expense>,
        val payments: List<StatePayment>,
        val transfers: List<Transfer>
    )

    private fun parseRecords(records: List<RecordItem>): ParsedRecords {
        val invoices = mutableListOf<Invoice>()
        val expenses = mutableListOf<Expense>()
        val payments = mutableListOf<StatePayment>()
        val transfers = mutableListOf<Transfer>()

        records.forEach { item ->
            val payloadNode = JsonSupport.mapper.readTree(item.payloadJson)
            when (item.recordType) {
                RecordType.INVOICE -> invoices += (RecordPayloadParser.parse(item.recordType, payloadNode) as Invoice)
                RecordType.EXPENSE -> expenses += (RecordPayloadParser.parse(item.recordType, payloadNode) as Expense)
                RecordType.STATE_PAYMENT -> payments += (RecordPayloadParser.parse(item.recordType, payloadNode) as StatePayment)
                RecordType.TRANSFER -> transfers += (RecordPayloadParser.parse(item.recordType, payloadNode) as Transfer)
                RecordType.BUDGET -> Unit
            }
        }

        return ParsedRecords(
            invoices = invoices,
            expenses = expenses,
            payments = payments,
            transfers = transfers
        )
    }

    private fun registerParsed(domainService: DefaultDomainService, parsed: ParsedRecords) {
        parsed.invoices.forEach(domainService::registerInvoice)
        parsed.expenses.forEach(domainService::registerExpense)
        parsed.payments.forEach(domainService::recordStatePayment)
        parsed.transfers.forEach(domainService::recordTransfer)
    }
}
