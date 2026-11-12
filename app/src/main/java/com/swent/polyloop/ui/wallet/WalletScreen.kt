// Made with Claude.

package com.swent.polyloop.ui.wallet

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.wallet.Wallet
import com.swent.polyloop.model.wallet.WalletTransaction
import com.swent.polyloop.model.wallet.WalletTransactionType
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.OfflineBanner
import com.swent.polyloop.ui.components.formatDay
import com.swent.polyloop.ui.theme.PositiveGreen
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Wallet screen (Figma frame 27): the balance, the PolyPoints held, a Recharge button and the
 * transaction history. It reads [viewModel] and changes no balance itself.
 *
 * @param viewModel the ViewModel whose state is shown and which is told to retry.
 * @param onRecharge called when the user taps "Recharge"; the recharge sheet (frame 28) is not part
 *   of this screen yet.
 */
@Composable
fun WalletScreen(viewModel: WalletViewModel, onRecharge: () -> Unit = {}) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  WalletContent(uiState = uiState, onRetry = viewModel::retry, onRecharge = onRecharge)
}

/**
 * Stateless Wallet content. While offline it shows the offline banner and disables "Recharge", but
 * the wallet and the history stay readable. While loading with no history it shows a spinner; on an
 * error with no history, the error with a Retry button; otherwise the balance card, the history
 * (newest first, as [uiState] gives it) and the footer, with a progress bar or the error above them
 * while a reload or a failure is pending.
 *
 * @param uiState what to show.
 * @param onRetry called when the user taps "Retry" under an error.
 * @param onRecharge called when the user taps "Recharge".
 * @param today the current day, to leave the year out of this year's dates.
 */
@Composable
fun WalletContent(
    uiState: WalletUiState,
    onRetry: () -> Unit,
    onRecharge: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.wallet_screen)
  ) {
    if (uiState.isOffline) OfflineBanner()
    Text(
        text = stringResource(R.string.wallet_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp).testTag(C.Tag.wallet_title),
    )
    when {
      uiState.isLoading && uiState.history.isEmpty() ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.wallet_loading))
          }
      uiState.errorMsg != null && uiState.history.isEmpty() ->
          WalletError(
              errorMsg = uiState.errorMsg,
              onRetry = onRetry,
              modifier = Modifier.fillMaxSize(),
          )
      else ->
          Column(
              modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            if (uiState.isLoading) {
              LinearProgressIndicator(
                  modifier = Modifier.fillMaxWidth().testTag(C.Tag.wallet_loading)
              )
            }
            uiState.errorMsg?.let {
              WalletError(errorMsg = it, onRetry = onRetry, modifier = Modifier.fillMaxWidth())
            }
            BalanceCard(uiState.wallet, enabled = !uiState.isOffline, onRecharge = onRecharge)
            HistorySection(uiState.history, today)
            Text(
                text = stringResource(R.string.wallet_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(C.Tag.wallet_footer),
            )
          }
    }
  }
}

/**
 * The balance card: the available PolyPoints, the amount held as deposits, the amount held for
 * rentals when it is above 0, and the Recharge button, which is disabled unless [enabled].
 *
 * @param wallet the balance and the amounts held.
 * @param enabled whether the Recharge button can be tapped; false while offline.
 * @param onRecharge called when the user taps "Recharge".
 */
@Composable
private fun BalanceCard(wallet: Wallet, enabled: Boolean, onRecharge: () -> Unit) {
  OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
          text = stringResource(R.string.wallet_available),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = wallet.available.toString(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.wallet_available),
        )
        Text(
            text = stringResource(R.string.wallet_polypoints),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
      }
      HeldRow(R.string.wallet_held_deposits, wallet.heldAsDeposits, C.Tag.wallet_held_deposits)
      if (wallet.heldForRentals > 0) {
        HeldRow(R.string.wallet_held_rentals, wallet.heldForRentals, C.Tag.wallet_held_rentals)
      }
      Button(
          onClick = onRecharge,
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.wallet_recharge_button),
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.wallet_recharge))
      }
    }
  }
}

/**
 * A line of the balance card: [labelRes] on the left, [amount] in PP on the right.
 *
 * @param labelRes the label of the line.
 * @param amount the amount in whole PP.
 * @param amountTag the test tag of the amount.
 */
@Composable
private fun HeldRow(@StringRes labelRes: Int, amount: Int, amountTag: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.wallet_pp_amount, amount),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag(amountTag),
    )
  }
}

/**
 * The "HISTORY" title and one card with a row per transaction, or the empty text.
 *
 * @param history the transactions to show, in the order given.
 * @param today the current day, to leave the year out of this year's dates.
 */
@Composable
private fun HistorySection(history: List<WalletTransaction>, today: LocalDate) {
  Column(
      modifier = Modifier.padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = stringResource(R.string.wallet_history_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
      if (history.isEmpty()) {
        Text(
            text = stringResource(R.string.wallet_history_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp).testTag(C.Tag.wallet_history_empty),
        )
      } else {
        // A plain Column, not a lazy one: the single rounded card holds every row at once.
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
          history.forEach { TransactionRow(it, today) }
        }
      }
    }
  }
}

/**
 * One line of the history: the type and then the item, or the date when there is no item ("Recharge
 * · 3 Oct", "Deposit held · Camping tent"), and the amount: "+N PP" in green when positive, "−N PP"
 * (a real minus sign, U+2212) in the normal text colour when negative, and "0 PP" with no sign
 * when 0.
 *
 * @param transaction the transaction to show.
 * @param today the current day, to leave the year out of this year's dates.
 */
@Composable
private fun TransactionRow(transaction: WalletTransaction, today: LocalDate) {
  val day = transaction.date.atZone(ZoneId.systemDefault()).toLocalDate()
  val detail = transaction.itemTitle.ifBlank { formatDay(day, withYear = day.year != today.year) }
  val amount = transaction.amount
  val amountText =
      when {
        amount > 0 -> stringResource(R.string.wallet_amount_positive, amount)
        amount < 0 -> stringResource(R.string.wallet_amount_negative, -amount)
        else -> stringResource(R.string.wallet_pp_amount, amount)
      }
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(vertical = 8.dp)
              .testTag(C.Tag.wallet_row_ + transaction.id),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
        text =
            stringResource(
                R.string.wallet_row_title,
                stringResource(transaction.type.labelRes),
                detail,
            ),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f).testTag(C.Tag.wallet_row_title),
    )
    Text(
        text = amountText,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (amount > 0) PositiveGreen else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 8.dp).testTag(C.Tag.wallet_row_amount),
    )
  }
}

/**
 * The load error ([errorMsg], or a generic text if it is blank) and a Retry button: on the whole
 * screen when there is no history, above the content otherwise.
 *
 * @param errorMsg the message to show; blank for the generic text.
 * @param onRetry called when the user taps "Retry".
 * @param modifier how the error is laid out.
 */
@Composable
private fun WalletError(errorMsg: String, onRetry: () -> Unit, modifier: Modifier) {
  Column(
      modifier = modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = errorMsg.ifBlank { stringResource(R.string.wallet_error_fallback) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.wallet_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.wallet_retry_button)) {
      Text(stringResource(R.string.wallet_retry))
    }
  }
}

@get:StringRes
private val WalletTransactionType.labelRes: Int
  get() =
      when (this) {
        WalletTransactionType.RECHARGE -> R.string.wallet_type_recharge
        WalletTransactionType.RENTAL_PAYMENT -> R.string.wallet_type_rental_payment
        WalletTransactionType.LATE_FEE -> R.string.wallet_type_late_fee
        WalletTransactionType.RENTAL_HELD -> R.string.wallet_type_rental_held
        WalletTransactionType.RENTAL_RELEASED -> R.string.wallet_type_rental_released
        WalletTransactionType.DEPOSIT_HELD -> R.string.wallet_type_deposit_held
        WalletTransactionType.DEPOSIT_RELEASED -> R.string.wallet_type_deposit_released
        WalletTransactionType.DEPOSIT_SPLIT -> R.string.wallet_type_deposit_split
        WalletTransactionType.DEPOSIT_FORFEITED -> R.string.wallet_type_deposit_forfeited
      }
