class StatementImport {
  final String id;
  final String bankAccountId;
  final String fileType;
  final String? extractionMethod;
  final String status;
  final String? errorCode;
  final int? pageCount;
  final int? rowCount;
  final int? flaggedCount;
  final num? openingBalance;
  final num? closingBalance;
  final String? gateStatus;
  final int selectedCount;
  final int blockingCount;
  final int? committedCount;
  final DateTime? createdAt;

  StatementImport({
    required this.id,
    required this.bankAccountId,
    required this.fileType,
    this.extractionMethod,
    required this.status,
    this.errorCode,
    this.pageCount,
    this.rowCount,
    this.flaggedCount,
    this.openingBalance,
    this.closingBalance,
    this.gateStatus,
    required this.selectedCount,
    required this.blockingCount,
    this.committedCount,
    this.createdAt,
  });

  factory StatementImport.fromJson(Map<String, dynamic> json) {
    return StatementImport(
      id: json['id'] as String,
      bankAccountId: json['bankAccountId'] as String? ?? '',
      fileType: json['fileType'] as String? ?? '',
      extractionMethod: json['extractionMethod'] as String?,
      status: json['status'] as String? ?? 'processing',
      errorCode: json['errorCode'] as String?,
      pageCount: json['pageCount'] as int?,
      rowCount: json['rowCount'] as int?,
      flaggedCount: json['flaggedCount'] as int?,
      openingBalance: json['openingBalance'] as num?,
      closingBalance: json['closingBalance'] as num?,
      gateStatus: json['gateStatus'] as String?,
      selectedCount: (json['selectedCount'] as num?)?.toInt() ?? 0,
      blockingCount: (json['blockingCount'] as num?)?.toInt() ?? 0,
      committedCount: json['committedCount'] as int?,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
    );
  }
}

class ExistingTransactionSummary {
  final String id;
  final num amount;
  final String direction;
  final DateTime transactionDate;
  final String? merchantRaw;

  ExistingTransactionSummary({
    required this.id,
    required this.amount,
    required this.direction,
    required this.transactionDate,
    this.merchantRaw,
  });

  factory ExistingTransactionSummary.fromJson(Map<String, dynamic> json) {
    return ExistingTransactionSummary(
      id: json['id'] as String,
      amount: json['amount'] as num? ?? 0,
      direction: json['direction'] as String? ?? 'debit',
      transactionDate: DateTime.tryParse(json['transactionDate'] as String? ?? '') ?? DateTime.now(),
      merchantRaw: json['merchantRaw'] as String?,
    );
  }
}

class StatementRow {
  final String id;
  final int rowIndex;
  final String? txnDate;
  final String? description;
  final num? amount;
  final String? direction;
  final num? balanceAfter;
  final num? confidence;
  final bool needsCheck;
  final String? checkReason;
  final bool selected;
  final ExistingTransactionSummary? existingTransaction;

  StatementRow({
    required this.id,
    required this.rowIndex,
    this.txnDate,
    this.description,
    this.amount,
    this.direction,
    this.balanceAfter,
    this.confidence,
    required this.needsCheck,
    this.checkReason,
    required this.selected,
    this.existingTransaction,
  });

  factory StatementRow.fromJson(Map<String, dynamic> json) {
    return StatementRow(
      id: json['id'] as String,
      rowIndex: (json['rowIndex'] as num?)?.toInt() ?? 0,
      txnDate: json['txnDate'] as String?,
      description: json['description'] as String?,
      amount: json['amount'] as num?,
      direction: json['direction'] as String?,
      balanceAfter: json['balanceAfter'] as num?,
      confidence: json['confidence'] as num?,
      needsCheck: json['needsCheck'] as bool? ?? false,
      checkReason: json['checkReason'] as String?,
      selected: json['selected'] as bool? ?? true,
      existingTransaction: json['existingTransaction'] != null
          ? ExistingTransactionSummary.fromJson(json['existingTransaction'] as Map<String, dynamic>)
          : null,
    );
  }
}
