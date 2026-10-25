# Subscription Billing System (Java, OOP)

A console-based **Subscription Billing** application that manages plans, subscribers, invoices and payments. It is built in plain Java to demonstrate core object-oriented programming concepts: inheritance, polymorphism, method overloading, method overriding and encapsulation.

## Features

- Monthly and annual plans, with an annual discount and optional free trial days
- Subscriber lifecycle: trial, active, suspended and cancelled
- Plan upgrades and downgrades with **proration** (extra charge or stored credit)
- Invoice generation with optional **discount codes** and automatic credit application
- Full and partial payments, with validation (overpayment is rejected)
- Overdue detection, automatic suspension and automatic reactivation after payment
- **Revenue report** and **aging report** (1-30, 31-60, 61-90, 90+ days)

## Project structure

```
SubscriptionBilling/
├── Plan.java            # Abstract base class for all plans
├── MonthlyPlan.java     # Extends Plan: billed monthly
├── AnnualPlan.java      # Extends Plan: billed yearly with a discount
├── Subscriber.java      # Customer and subscription status
├── Invoice.java         # Invoice with controlled state transitions
├── BillingService.java  # Business logic: billing, proration, payments, reports
├── BillingAppMain.java  # Demo / test driver
├── sample_output.txt    # Captured output of a full run
└── README.md
```

## Classes

| Class | Key fields | Key methods |
|---|---|---|
| `Plan` (abstract) | `planId`, `name`, `monthlyPrice`, `features`, `trialDays`, static `planCount` | `computeAmount()`, `getBillingCycleMonths()`, `getEffectiveMonthlyPrice()`, `addFeature()`, getters/setters |
| `MonthlyPlan` | inherits from `Plan` | overrides `computeAmount()` (price x 1) |
| `AnnualPlan` | `discountPercent` + inherited | overrides `computeAmount()` (price x 12 less discount) |
| `Subscriber` | `id`, `name`, `email`, `currentPlan`, `status`, billing dates, `creditBalance` | `subscribe()`, `changePlan()`, `suspend()`, `reactivate()`, `cancel()`, `addCredit()`, `useCredit()` |
| `Invoice` | `invoiceNo`, `subscriberId`, `amount`, `dueDate`, `state`, `amountPaid` | `applyPayment()`, `markOverdue()`, `cancel()`, `getBalance()`, `daysOverdue()` |
| `BillingService` | plans, subscribers, invoices, payments, discount codes, payment terms | `subscribe()`, `changePlan()`, `calculateProration()`, `generateInvoice()` (x3), `recordPayment()`, `runBillingCycle()`, `runOverdueCheck()`, `printRevenueReport()`, `printAgingReport()` |

## OOP concepts demonstrated

| Concept | Where |
|---|---|
| **Inheritance** | `MonthlyPlan` and `AnnualPlan` extend `Plan` |
| **Method overriding** | Each plan type overrides `computeAmount()`, `getBillingCycleMonths()` and `getPlanType()` |
| **Polymorphism** | Plans are stored as `Plan` references (`List<Plan>`), and the correct pricing runs at runtime |
| **Method overloading** | `BillingService.generateInvoice()` has three forms: `(subscriber, date)`, `(subscriber, date, discountCode)` and `(subscriber, date, amount, description)` |
| **Encapsulation** | Private fields. `Subscriber.status` and `Invoice.state` change only through validated methods that throw exceptions on illegal transitions |
| **Abstraction** | `Plan` is abstract and defines the contract that subclasses implement |

## How proration works

When a subscriber changes plan mid-cycle:

```
fraction = days remaining / days in cycle
credit   = old plan cycle price x fraction
charge   = new plan monthly-equivalent price x old cycle months x fraction
net      = charge - credit
```

A positive `net` produces a proration invoice. A negative `net` is stored as credit and applied to the next invoice.

## Requirements

- Java 8 or later (JDK)

## Build and run


git clone <your-repo-url>
cd SubscriptionBilling
javac *.java
java BillingAppMain

## Demo scenario (`BillingAppMain`)

1. Create three plans: Basic (monthly, 299), Pro (monthly, 799, 7-day trial), Premium (annual, 15% off, 14-day trial).
2. Create four subscribers and subscribe them.
3. Generate invoices, including one with the `WELCOME10` discount code.
4. Trials end and the first invoices are issued. Record full, partial and rejected (overpayment) payments.
5. Upgrade Ravi (proration invoice) and downgrade Asha (credit), and reject a change to the same plan.
6. Run the next billing cycles. Asha's credit fully covers her invoice.
7. Run the overdue check on 2026-03-15: invoices become OVERDUE and Meena and Karthik are suspended.
8. Print the aging report, record Karthik's payments (he is automatically reactivated) and print the revenue report.

## Sample output



=== 1. Create plans (stored as Plan references) ===
  P-BASIC [Basic | Monthly] list 299.00/month, per-cycle 299.00, trial 0 days, features=[1 user, 5 GB storage, Email support]
    -> charge per cycle (MonthlyPlan): 299.00
  P-PRO [Pro | Monthly] list 799.00/month, per-cycle 799.00, trial 7 days, features=[10 users, 100 GB storage, Priority support]
    -> charge per cycle (MonthlyPlan): 799.00
  P-PREM [Premium | Annual (15.0% off)] list 1499.00/month, per-cycle 15289.80, trial 14 days, features=[Unlimited users, 1 TB storage, 24x7 support]
    -> charge per cycle (AnnualPlan): 15289.80
  Plans created: 3

=== 2. Create subscribers and subscribe ===
  Asha subscribed to Pro on 2026-01-01 -> status TRIAL (trial ends 2026-01-08)
  Ravi subscribed to Basic on 2026-01-01 -> status ACTIVE
  Meena subscribed to Premium on 2026-01-01 -> status TRIAL (trial ends 2026-01-15)
  Karthik subscribed to Basic on 2026-01-01 -> status ACTIVE
  [Encapsulation] Ravi already has a subscription (ACTIVE)

=== 3. Generate invoices on 2026-01-01 (overloaded generateInvoice) ===
  With a discount code (Karthik):
  Invoice created: INV-0001 | S004 | amt    269.10 | paid      0.00 | due 2026-01-16 | ISSUED         | Basic (Monthly) less 10.0% (WELCOME10)
  Billing run for everyone else due today (no discount code):
  -- Billing run for 2026-01-01 --
  Invoice created: INV-0002 | S002 | amt    299.00 | paid      0.00 | due 2026-01-16 | ISSUED         | Basic (Monthly)

=== 4. Payments ===
  Payment 299.00 (UPI) on 2026-01-05 -> INV-0002 is now PAID, balance 0.00

=== 5. Trial ends: Asha (Jan 8) and Meena (Jan 15) ===
  -- Billing run for 2026-01-08 --
  Invoice created: INV-0003 | S001 | amt    799.00 | paid      0.00 | due 2026-01-23 | ISSUED         | Pro (Monthly)
  Payment 799.00 (CARD) on 2026-01-10 -> INV-0003 is now PAID, balance 0.00
  -- Billing run for 2026-01-15 --
  Invoice created: INV-0004 | S003 | amt  15289.80 | paid      0.00 | due 2026-01-30 | ISSUED         | Premium (Annual (15.0% off))
  Payment 10000.00 (BANK) on 2026-01-20 -> INV-0004 is now PARTIALLY_PAID, balance 5289.80
  Try to overpay (rejected by Invoice):
  [PAYMENT REJECTED] Payment 999999.00 exceeds balance 5289.80

=== 6. Change plans with proration ===
  Ravi upgrades Basic -> Pro on Jan 16:
  Proration: 16 of 31 days left | credit for Basic 154.32 | charge for Pro 412.39 | net 258.06
  Ravi changed plan Basic -> Pro on 2026-01-16
  Invoice created: INV-0005 | S002 | amt    258.06 | paid      0.00 | due 2026-01-31 | ISSUED         | Proration: Basic -> Pro
  Payment 258.06 (UPI) on 2026-01-18 -> INV-0005 is now PAID, balance 0.00
  Asha downgrades Pro -> Basic on Jan 20 (generates a credit):
  Proration: 19 of 31 days left | credit for Pro 489.71 | charge for Basic 183.26 | net -306.45
  Asha changed plan Pro -> Basic on 2026-01-20
  Downgrade credit of 306.45 stored for Asha
  Asha tries to 'change' to the plan she already has:
  [CHANGE REJECTED] Asha is already on plan Basic

=== 7. Next billing cycles (Feb 1 and Feb 8) ===
  -- Billing run for 2026-02-01 --
  Invoice created: INV-0006 | S002 | amt    799.00 | paid      0.00 | due 2026-02-16 | ISSUED         | Pro (Monthly)
  Invoice created: INV-0007 | S004 | amt    299.00 | paid      0.00 | due 2026-02-16 | ISSUED         | Basic (Monthly)
  -- Billing run for 2026-02-08 --
  Invoice created: INV-0008 | S001 | amt      0.00 | paid      0.00 | due 2026-02-23 | PAID           | Basic (Monthly), credit applied 299.00

=== 8. Overdue check and aging report (2026-03-15) ===
  -- Overdue check as of 2026-03-15 --
  INV-0001 marked OVERDUE (58 days late)
  INV-0004 marked OVERDUE (44 days late)
  INV-0006 marked OVERDUE (27 days late)
  INV-0007 marked OVERDUE (27 days late)
  Meena SUSPENDED: INV-0004 is 44 days overdue
  Karthik SUSPENDED: INV-0001 is 58 days overdue
  ===== AGING REPORT as of 2026-03-15 =====
  31-60 days INV-0001 Karthik  balance    269.10 due 2026-01-16 (58 days overdue)
  31-60 days INV-0004 Meena    balance   5289.80 due 2026-01-30 (44 days overdue)
  1-30 days  INV-0006 Ravi     balance    799.00 due 2026-02-16 (27 days overdue)
  1-30 days  INV-0007 Karthik  balance    299.00 due 2026-02-16 (27 days overdue)
  Bucket summary:
    1-30 days  : 2 invoice(s),   1098.00
    31-60 days : 2 invoice(s),   5558.90
    61-90 days : 0 invoice(s),      0.00
    90+ days   : 0 invoice(s),      0.00
    TOTAL      :   6656.90

=== 9. Subscribers and invoice register ===
  S001 | Asha     | asha@example.com       | plan=Basic    | status=ACTIVE    | next bill=2026-03-08 | credit=7.45
  S002 | Ravi     | ravi@example.com       | plan=Pro      | status=ACTIVE    | next bill=2026-03-01 | credit=0.00
  S003 | Meena    | meena@example.com      | plan=Premium  | status=SUSPENDED | next bill=2027-01-15 | credit=0.00
  S004 | Karthik  | karthik@example.com    | plan=Basic    | status=SUSPENDED | next bill=2026-03-01 | credit=0.00

  INV-0001 | S004 | amt    269.10 | paid      0.00 | due 2026-01-16 | OVERDUE        | Basic (Monthly) less 10.0% (WELCOME10)
  INV-0002 | S002 | amt    299.00 | paid    299.00 | due 2026-01-16 | PAID           | Basic (Monthly)
  INV-0003 | S001 | amt    799.00 | paid    799.00 | due 2026-01-23 | PAID           | Pro (Monthly)
  INV-0004 | S003 | amt  15289.80 | paid  10000.00 | due 2026-01-30 | OVERDUE        | Premium (Annual (15.0% off))
  INV-0005 | S002 | amt    258.06 | paid    258.06 | due 2026-01-31 | PAID           | Proration: Basic -> Pro
  INV-0006 | S002 | amt    799.00 | paid      0.00 | due 2026-02-16 | OVERDUE        | Pro (Monthly)
  INV-0007 | S004 | amt    299.00 | paid      0.00 | due 2026-02-16 | OVERDUE        | Basic (Monthly)
  INV-0008 | S001 | amt      0.00 | paid      0.00 | due 2026-02-23 | PAID           | Basic (Monthly), credit applied 299.00

=== 10. Karthik pays everything on Mar 16 -> reactivated ===
  Payment 269.10 (CARD) on 2026-03-16 -> INV-0001 is now PAID, balance 0.00
  Payment 299.00 (CARD) on 2026-03-16 -> INV-0007 is now PAID, balance 0.00
  Karthik has no overdue invoices -> account REACTIVATED

=== 11. Revenue report ===
  ===== REVENUE REPORT =====
  Invoices issued : 8
  Total invoiced  :   18012.96
  Total collected :   11924.16
  Outstanding     :    6088.80
  Collection rate :      66.2%
  Collected by plan:
    Basic        867.10
    Premium    10000.00
    Pro         1057.06
  Collected by method:
    BANK       10000.00
    CARD        1367.10
    UPI          557.06
  Invoices by state: {PAID=6, OVERDUE=2}

  ===== AGING REPORT as of 2026-03-16 =====
  31-60 days INV-0004 Meena    balance   5289.80 due 2026-01-30 (45 days overdue)
  1-30 days  INV-0006 Ravi     balance    799.00 due 2026-02-16 (28 days overdue)
  Bucket summary:
    1-30 days  : 1 invoice(s),    799.00
    31-60 days : 1 invoice(s),   5289.80
    61-90 days : 0 invoice(s),      0.00
    90+ days   : 0 invoice(s),      0.00
    TOTAL      :   6088.80
```



## License

Released for educational use. Add a license of your choice (for example MIT) before publishing.
