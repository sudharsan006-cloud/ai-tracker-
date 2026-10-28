class Category {
  final String id;
  final String name;
  final String type;
  final String? icon;
  final bool isSystem;

  Category({
    required this.id,
    required this.name,
    required this.type,
    this.icon,
    required this.isSystem,
  });

  factory Category.fromJson(Map<String, dynamic> json) {
    return Category(
      id: json['id'] as String,
      name: json['name'] as String,
      type: json['type'] as String,
      icon: json['icon'] as String?,
      isSystem: json['isSystem'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'type': type,
      'icon': icon,
      'isSystem': isSystem,
    };
  }
}

class Transaction {
  final String id;
  final String bankAccountId;
  final int currencyId;
  final String? categoryId;
  final num amount;
  final String direction;
  final String? merchantRaw;
  final DateTime transactionDate;
  final String source;
  final bool isSelfTransfer;
  final String reviewStatus;
  final String? reviewPartnerId;
  final String? selfTransferMatchedId;
  final String? refundMatchedId;

  Transaction({
    required this.id,
    required this.bankAccountId,
    required this.currencyId,
    this.categoryId,
    required this.amount,
    required this.direction,
    this.merchantRaw,
    required this.transactionDate,
    required this.source,
    required this.isSelfTransfer,
    required this.reviewStatus,
    this.reviewPartnerId,
    this.selfTransferMatchedId,
    this.refundMatchedId,
  });

  String get amountFormatted => amount.toStringAsFixed(2);

  factory Transaction.fromJson(Map<String, dynamic> json) {
    return Transaction(
      id: json['id'] as String,
      bankAccountId: json['bankAccountId'] as String,
      currencyId: json['currencyId'] as int,
      categoryId: json['categoryId'] as String?,
      amount: json['amount'] as num,
      direction: json['direction'] as String,
      merchantRaw: json['merchantRaw'] as String?,
      transactionDate: DateTime.parse(json['transactionDate'] as String),
      source: json['source'] as String,
      isSelfTransfer: json['isSelfTransfer'] as bool? ?? false,
      reviewStatus: json['reviewStatus'] as String? ?? 'none',
      reviewPartnerId: json['reviewPartnerId'] as String?,
      selfTransferMatchedId: json['selfTransferMatchedId'] as String?,
      refundMatchedId: json['refundMatchedId'] as String?,
    );
  }
}

class TransactionPage {
  final List<Transaction> items;
  final int page;
  final int size;
  final int total;

  TransactionPage({
    required this.items,
    required this.page,
    required this.size,
    required this.total,
  });

  factory TransactionPage.fromJson(Map<String, dynamic> json) {
    final list = json['items'] as List<dynamic>? ?? [];
    return TransactionPage(
      items: list.map((e) => Transaction.fromJson(e as Map<String, dynamic>)).toList(),
      page: json['page'] as int? ?? 0,
      size: json['size'] as int? ?? 50,
      total: json['total'] as int? ?? 0,
    );
  }
}

class Budget {
  final String id;
  final String? categoryId;
  final int currencyId;
  final num amount;
  final String period;
  final num spent;
  final num remaining;
  final num percentUsed;
  final bool overBudget;
  final DateTime? periodStart;
  final DateTime? periodEnd;

  Budget({
    required this.id,
    this.categoryId,
    required this.currencyId,
    required this.amount,
    required this.period,
    required this.spent,
    required this.remaining,
    required this.percentUsed,
    required this.overBudget,
    this.periodStart,
    this.periodEnd,
  });

  String get amountFormatted => amount.toStringAsFixed(2);
  String get spentFormatted => spent.toStringAsFixed(2);
  String get remainingFormatted => remaining.toStringAsFixed(2);
  String get percentUsedFormatted => percentUsed.toStringAsFixed(1);

  factory Budget.fromJson(Map<String, dynamic> json) {
    return Budget(
      id: json['id'] as String,
      categoryId: json['categoryId'] as String?,
      currencyId: json['currencyId'] as int,
      amount: json['amount'] as num,
      period: json['period'] as String? ?? 'monthly',
      spent: json['spent'] as num? ?? 0,
      remaining: json['remaining'] as num? ?? 0,
      percentUsed: json['percentUsed'] as num? ?? 0,
      overBudget: json['overBudget'] as bool? ?? false,
      periodStart: json['periodStart'] != null ? DateTime.parse(json['periodStart'] as String) : null,
      periodEnd: json['periodEnd'] != null ? DateTime.parse(json['periodEnd'] as String) : null,
    );
  }
}

class TransactionPair {
  final Transaction transaction;
  final Transaction partner;

  TransactionPair({
    required this.transaction,
    required this.partner,
  });

  factory TransactionPair.fromJson(Map<String, dynamic> json) {
    return TransactionPair(
      transaction: Transaction.fromJson(json['transaction'] as Map<String, dynamic>),
      partner: Transaction.fromJson(json['partner'] as Map<String, dynamic>),
    );
  }
}

class UnparsedSmsItem {
  final String id;
  final String rawText;
  final String? sender;
  final DateTime receivedAt;

  UnparsedSmsItem({
    required this.id,
    required this.rawText,
    this.sender,
    required this.receivedAt,
  });

  factory UnparsedSmsItem.fromJson(Map<String, dynamic> json) {
    return UnparsedSmsItem(
      id: json['id'] as String,
      rawText: json['rawText'] as String,
      sender: json['sender'] as String?,
      receivedAt: DateTime.parse(json['receivedAt'] as String),
    );
  }
}

class ReviewQueue {
  final List<TransactionPair> duplicates;
  final List<TransactionPair> selfTransfers;
  final List<TransactionPair> refundMatches;
  final List<UnparsedSmsItem> unparsedSms;

  ReviewQueue({
    required this.duplicates,
    required this.selfTransfers,
    required this.refundMatches,
    required this.unparsedSms,
  });

  int get totalCount =>
      duplicates.length + selfTransfers.length + refundMatches.length + unparsedSms.length;

  factory ReviewQueue.fromJson(Map<String, dynamic> json) {
    final dupList = json['duplicates'] as List<dynamic>? ?? [];
    final stList = json['selfTransfers'] as List<dynamic>? ?? [];
    final refList = json['refundMatches'] as List<dynamic>? ?? [];
    final smsList = json['unparsedSms'] as List<dynamic>? ?? [];

    return ReviewQueue(
      duplicates: dupList.map((e) => TransactionPair.fromJson(e as Map<String, dynamic>)).toList(),
      selfTransfers: stList.map((e) => TransactionPair.fromJson(e as Map<String, dynamic>)).toList(),
      refundMatches: refList.map((e) => TransactionPair.fromJson(e as Map<String, dynamic>)).toList(),
      unparsedSms: smsList.map((e) => UnparsedSmsItem.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }
}
