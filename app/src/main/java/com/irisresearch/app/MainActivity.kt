package com.irisresearch.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc
import org.opencv.core.Size
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var analyze: Button
    private lateinit var result: TextView
    private lateinit var searchBox: EditText
    private lateinit var searchResult: TextView
    private var bitmap: Bitmap? = null
    private var bookText = ""
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        imageView = findViewById(R.id.eyeImage)
        analyze = findViewById(R.id.analyzeButton)
        result = findViewById(R.id.result)
        searchBox = findViewById(R.id.searchBox)
        searchResult = findViewById(R.id.searchResult)
        bookText = assets.open("knowledge_fa.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }

        findViewById<Button>(R.id.pickButton).setOnClickListener { pickImage() }
        analyze.setOnClickListener { analyzeIris() }
        findViewById<Button>(R.id.searchButton).setOnClickListener { searchBook() }
        findViewById<Button>(R.id.protocolButton).setOnClickListener { showProtocol() }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 100 || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return

        try {
            val original = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            bitmap?.takeIf { !it.isRecycled }?.recycle()
            bitmap = scaleForPreview(original, 1600)
            if (bitmap !== original && !original.isRecycled) original.recycle()
            imageView.setImageBitmap(bitmap)
            analyze.isEnabled = true
            result.text = "تصویر دریافت شد. برای تحلیل پژوهشی روی دکمه تحلیل بزنید."
        } catch (e: Exception) {
            analyze.isEnabled = false
            result.text = "خواندن تصویر ناموفق بود: ${e.localizedMessage ?: "خطای نامشخص"}"
        }
    }

    private fun analyzeIris() {
        val srcBmp = bitmap ?: run {
            result.text = "ابتدا یک تصویر انتخاب کنید."
            return
        }

        analyze.isEnabled = false
        result.text = "در حال تحلیل تصویر..."

        analysisExecutor.execute {
            var mat: Mat? = null
            var gray: Mat? = null
            var blurred: Mat? = null
            var circles: Mat? = null
            var std: MatOfDouble? = null

            try {
                // HoughCircles can consume a lot of native memory on camera/gallery images.
                // Work on a bounded copy so very large images cannot crash the app.
                val workBmp = scaleForAnalysis(srcBmp, 1024)
                mat = Mat()
                Utils.bitmapToMat(workBmp, mat)
                if (workBmp !== srcBmp && !workBmp.isRecycled) workBmp.recycle()

                gray = Mat()
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
                blurred = Mat()
                Imgproc.GaussianBlur(gray, blurred, Size(9.0, 9.0), 2.0)

                circles = Mat()
                val minDim = min(blurred.rows(), blurred.cols()).toDouble()
                val minRadius = (minDim / 12.0).roundToInt().coerceAtLeast(8)
                val maxRadius = (minDim / 2.0).roundToInt().coerceAtLeast(minRadius + 1)
                val minDist = (blurred.rows() / 8.0).coerceAtLeast(20.0)

                Imgproc.HoughCircles(
                    blurred,
                    circles,
                    Imgproc.HOUGH_GRADIENT,
                    1.2,
                    minDist,
                    100.0,
                    30.0,
                    minRadius,
                    maxRadius
                )

                val mean = Core.mean(gray).`val`[0]
                std = MatOfDouble()
                val meanMat = MatOfDouble()
                Core.meanStdDev(gray, meanMat, std)
                val contrast = if (std!!.total() > 0) std!![0, 0][0] else 0.0
                meanMat.release()

                val sb = StringBuilder()
                sb.append("گزارش مشاهده‌محور پژوهشی\n\n")
                sb.append("میانگین روشنایی تصویر: %.1f\n".format(Locale.US, mean))
                sb.append("کنتراست تقریبی: %.1f\n".format(Locale.US, contrast))
                sb.append("\nتصویر برای پایداری تحلیل به حداکثر ضلع ۱۰۲۴ پیکسل کاهش داده شد.\n")

                if (circles!!.cols() > 0) {
                    val c = circles!!.get(0, 0)
                    sb.append("دایره احتمالی عنبیه/ناحیه: مرکز (${c[0].roundToInt()}, ${c[1].roundToInt()})، شعاع ${c[2].roundToInt()} پیکسل\n")
                    sb.append("این تشخیص هندسی تقریبی است و باید توسط پژوهشگر بازبینی شود.\n")
                } else {
                    sb.append("دایره قابل‌اعتماد برای عنبیه پیدا نشد؛ کیفیت/زاویه عکس یا نور را بررسی کنید.\n")
                }

                sb.append("\nویژگی‌های قابل ثبت در نسخه پژوهشی: رنگ غالب، شکل مردمک، حلقه کولارت، تراکم فیبرها، لاکوناها و لکه‌ها. این نسخه آن‌ها را «مشاهده تصویری» می‌داند، نه تشخیص پزشکی.\n")
                sb.append("\nتفاسیر کتاب فقط به‌عنوان «دیدگاه سنتی منبع» قابل گزارش‌اند. برای آزمون علمی، هر ادعا باید از پیش تعریف، کورسازی، نمونه مستقل و مرجع پزشکی معتبر داشته باشد.")

                val text = sb.toString()
                runOnUiThread {
                    result.text = text
                    analyze.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    result.text = "تحلیل انجام نشد. لطفاً عکس واضح‌تر و کم‌حجم‌تری انتخاب کنید.\n\nجزئیات خطا: ${e.localizedMessage ?: "خطای نامشخص"}"
                    analyze.isEnabled = true
                }
            } finally {
                std?.release()
                circles?.release()
                blurred?.release()
                gray?.release()
                mat?.release()
            }
        }
    }

    private fun scaleForAnalysis(source: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = max(source.width, source.height)
        if (maxSide <= maxDimension) return source
        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun scaleForPreview(source: Bitmap, maxDimension: Int): Bitmap = scaleForAnalysis(source, maxDimension)

    private fun searchBook() {
        val q = searchBox.text.toString().trim()
        if (q.isEmpty()) {
            searchResult.text = "عبارت جستجو را وارد کنید."
            return
        }
        val normalized = q.replace("ي", "ی").replace("ك", "ک")
        val hits = bookText.lines().filter {
            it.replace("ي", "ی").replace("ك", "ک").contains(normalized, ignoreCase = true)
        }
        searchResult.text = if (hits.isEmpty()) "نتیجه‌ای در متن داخلی کتاب پیدا نشد." else hits.take(12).joinToString("\n\n")
    }

    private fun showProtocol() {
        AlertDialog.Builder(this)
            .setTitle("پروتکل اعتبارسنجی پزشکی")
            .setMessage("۱) ادعای عنبیه‌شناسی را دقیقاً و پیشاپیش تعریف کنید.\n\n۲) عکس عنبیه با پروتکل ثابت و کد ناشناس ثبت شود.\n\n۳) ارزیابی عنبیه‌شناسی از تشخیص مرجع پزشکی کور باشد و ارزیاب پزشکی نیز از نتیجه عنبیه‌شناسی بی‌خبر باشد.\n\n۴) مرجع پزشکی باید آزمون/معاینه معتبر و از پیش تعیین‌شده باشد.\n\n۵) شاخص‌ها: حساسیت، ویژگی، PPV، NPV، نسبت‌های درست‌نمایی، دقت، ROC/AUC و توافق بین ارزیاب‌ها (مثلاً Cohen's kappa).\n\n۶) تحلیل باید روی مجموعه آزمون مستقل انجام شود؛ آموزش/تنظیم و آزمون را جدا کنید.\n\n۷) خروجی برنامه نباید برای تصمیم درمانی استفاده شود. هدف این ماژول آزمون ادعاهاست، نه تشخیص بیمار.")
            .setPositiveButton("باشه", null).show()
    }

    override fun onDestroy() {
        analysisExecutor.shutdownNow()
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        super.onDestroy()
    }
}
