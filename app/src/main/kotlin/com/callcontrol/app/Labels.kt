package com.callcontrol.app

import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.InvalidReason
import com.callcontrol.domain.Mode
import com.callcontrol.storage.RepoError
import com.callcontrol.storage.ScheduleProblem
import com.callcontrol.storage.ThemeMode
import java.time.DayOfWeek
import java.time.LocalTime

/** All Arabic wording that depends on domain values. UI-independent so it can be reused and tested. */

fun Mode.label(): String = when (this) {
    Mode.AVAILABLE -> "متاح"
    Mode.BUSY -> "مشغول"
    Mode.UNAVAILABLE -> "غير متاح"
    Mode.SILENT -> "صامت"
}

/** Honest explanation of what each state really does on the phone (and what it cannot promise). */
fun Mode.description(): String = when (this) {
    Mode.AVAILABLE -> "تُسمح المكالمة ويرنّ الهاتف بشكل طبيعي."
    Mode.BUSY -> "يرفض الهاتف المكالمة. ما يسمعه المتصل (نغمة مشغول أو بريد صوتي أو رسالة) تحدده شبكة الاتصالات ولا يضمنه التطبيق."
    Mode.UNAVAILABLE -> "يرفض الهاتف المكالمة بنفس طريقة «مشغول». لا يستطيع التطبيق جعل الشبكة تعلن أن الهاتف مغلق أو خارج التغطية."
    Mode.SILENT -> "تصل المكالمة ويُكتم الرنين فقط، ولا تُرفض. يسمع المتصل نغمة الرنين العادية."
}

fun DecisionReason.label(): String = when (this) {
    DecisionReason.CONTROL_DISABLED -> "التحكم متوقف"
    DecisionReason.NUMBER_RULE -> "قاعدة رقم"
    DecisionReason.UNREGISTERED_RULE -> "رقم غير مسجل"
    DecisionReason.HIDDEN_RULE -> "رقم مخفي"
    DecisionReason.GLOBAL_SCHEDULE -> "الجدولة"
    DecisionReason.GLOBAL_MODE -> "الوضع العام"
    DecisionReason.INTERNAL_ERROR -> "خطأ داخلي (سُمحت المكالمة)"
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "حسب النظام"
    ThemeMode.LIGHT -> "فاتح"
    ThemeMode.DARK -> "داكن"
}

/** Saturday first, as the week starts in Egypt. */
val WEEK_ORDER: List<DayOfWeek> = listOf(
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

fun DayOfWeek.label(): String = when (this) {
    DayOfWeek.SATURDAY -> "السبت"
    DayOfWeek.SUNDAY -> "الأحد"
    DayOfWeek.MONDAY -> "الاثنين"
    DayOfWeek.TUESDAY -> "الثلاثاء"
    DayOfWeek.WEDNESDAY -> "الأربعاء"
    DayOfWeek.THURSDAY -> "الخميس"
    DayOfWeek.FRIDAY -> "الجمعة"
}

fun daysSummary(days: Set<DayOfWeek>): String = when {
    days.size == 7 -> "كل يوم"
    days.isEmpty() -> "لا أيام"
    else -> WEEK_ORDER.filter { it in days }.joinToString("، ") { it.label() }
}

fun formatTime(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

/**
 * Keeps a number readable inside right-to-left text: without the isolate marks a "+20..." number can be
 * displayed as "...20+".
 */
fun ltr(text: String): String = "\u2066$text\u2069"

/** Egyptian numbers are shown in the familiar local form (01012345678); everything else as stored. */
fun displayNumber(key: String?): String = when {
    key == null -> "رقم مخفي"
    key.startsWith("+20") -> "0" + key.removePrefix("+20")
    else -> key
}

fun RepoError.toMessage(): String = when (this) {
    is RepoError.InvalidNumber -> when (reason) {
        InvalidReason.EMPTY -> "اكتب رقم الهاتف."
        InvalidReason.ILLEGAL_CHARACTERS -> "الرقم يحتوي على رموز غير مسموحة."
        InvalidReason.INVALID_LENGTH -> "طول الرقم غير صحيح."
        InvalidReason.INVALID_EGYPT_NUMBER -> "رقم مصري غير صحيح. مثال: 01012345678"
        InvalidReason.UNRECOGNIZED_FORMAT -> "صيغة الرقم غير معروفة. ابدأ بـ 0 أو +20 أو برمز الدولة."
    }
    RepoError.DuplicateNumber -> "هذا الرقم محفوظ بالفعل في قاعدة أخرى."
    RepoError.NotFound -> "العنصر غير موجود (ربما حُذف)."
    is RepoError.InvalidSchedule -> when (problem) {
        ScheduleProblem.NO_DAYS -> "اختر يومًا واحدًا على الأقل."
        ScheduleProblem.EMPTY_INTERVAL -> "وقت البداية والنهاية متطابقان."
        ScheduleProblem.UNSUPPORTED_TIME_PRECISION -> "وقت غير مدعوم."
    }
    RepoError.InvalidOrder -> "ترتيب غير صالح."
    is RepoError.Storage -> "تعذّر الوصول إلى التخزين على الجهاز ولم يُحفظ التغيير. حاول مرة أخرى."
}
