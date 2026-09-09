package com.irisresearch.app

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var analyze: Button
    private lateinit var result: TextView
    private lateinit var searchBox: EditText
    private lateinit var searchResult: TextView
    private var bitmap: Bitmap? = null
    private var bookText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        imageView = findViewById(R.id.eyeImage); analyze = findViewById(R.id.analyzeButton)
        result = findViewById(R.id.result); searchBox = findViewById(R.id.searchBox); searchResult = findViewById(R.id.searchResult)
        bookText = assets.open("knowledge_fa.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        findViewById<Button>(R.id.pickButton).setOnClickListener { pickImage() }
        analyze.setOnClickListener { analyzeIris() }
        findViewById<Button>(R.id.searchButton).setOnClickListener { searchBook() }
        findViewById<Button>(R.id.protocolButton).setOnClickListener { showProtocol() }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            imageView.setImageBitmap(bitmap); analyze.isEnabled = true
            result.text = "تصویر دریافت شد. برای تحلیل پژوهشی روی دکمه تحلیل بزنید."
        }
    }

    private fun analyzeIris() {
        val srcBmp = bitmap ?: return
        val mat = Mat(); Utils.bitmapToMat(srcBmp, mat)
        val gray = Mat(); Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(9.0,9.0), 2.0)
        val circles = Mat()
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.2, gray.rows()/8.0, 100.0, 30.0, min(gray.rows(),gray.cols())/12, min(gray.rows(),gray.cols())/2)
        val mean = Core.mean(gray).`val`[0]
        val std = MatOfDouble(); Core.meanStdDev(gray, MatOfDouble(), std)
        val contrast = if (std.rows() > 0) std[0,0][0] else 0.0
        val sb = StringBuilder()
        sb.append("گزارش مشاهده‌محور پژوهشی\n\n")
        sb.append("میانگین روشنایی تصویر: %.1f\nکنتراست تقریبی: %.1f\n".format(Locale.US, mean, contrast))
        if (circles.cols() > 0) {
            val c = circles.get(0,0)
            sb.append("دایره احتمالی عنبیه/ناحیه: مرکز (${c[0].roundToInt()}, ${c[1].roundToInt()})، شعاع ${c[2].roundToInt()} پیکسل\n")
            sb.append("این تشخیص هندسی تقریبی است و باید توسط پژوهشگر بازبینی شود.\n")
        } else sb.append("دایره قابل‌اعتماد برای عنبیه پیدا نشد؛ کیفیت/زاویه عکس یا نور را بررسی کنید.\n")
        sb.append("\nویژگی‌های قابل ثبت در نسخه پژوهشی: رنگ غالب، شکل مردمک، حلقه کولارت، تراکم فیبرها، لاکوناها و لکه‌ها. این نسخه آن‌ها را «مشاهده تصویری» می‌داند، نه تشخیص پزشکی.\n")
        sb.append("\nتفاسیر کتاب فقط به‌عنوان «دیدگاه سنتی منبع» قابل گزارش‌اند. برای آزمون علمی، هر ادعا باید از پیش تعریف، کورسازی، نمونه مستقل و مرجع پزشکی معتبر داشته باشد.")
        result.text = sb.toString()
        mat.release(); gray.release(); circles.release()
    }

    private fun searchBook() {
        val q = searchBox.text.toString().trim()
        if (q.isEmpty()) { searchResult.text = "عبارت جستجو را وارد کنید."; return }
        val normalized = q.replace("ي","ی").replace("ك","ک")
        val lines = bookText.lines()
        val hits = lines.filter { it.replace("ي","ی").replace("ك","ک").contains(normalized, ignoreCase=true) }
        searchResult.text = if (hits.isEmpty()) "نتیجه‌ای در متن داخلی کتاب پیدا نشد." else hits.take(12).joinToString("\n\n")
    }

    private fun showProtocol() {
        AlertDialog.Builder(this)
            .setTitle("پروتکل اعتبارسنجی پزشکی")
            .setMessage("۱) ادعای عنبیه‌شناسی را دقیقاً و پیشاپیش تعریف کنید.\n\n۲) عکس عنبیه با پروتکل ثابت و کد ناشناس ثبت شود.\n\n۳) ارزیابی عنبیه‌شناسی از تشخیص مرجع پزشکی کور باشد و ارزیاب پزشکی نیز از نتیجه عنبیه‌شناسی بی‌خبر باشد.\n\n۴) مرجع پزشکی باید آزمون/معاینه معتبر و از پیش تعیین‌شده باشد.\n\n۵) شاخص‌ها: حساسیت، ویژگی، PPV، NPV، نسبت‌های درست‌نمایی، دقت، ROC/AUC و توافق بین ارزیاب‌ها (مثلاً Cohen's kappa).\n\n۶) تحلیل باید روی مجموعه آزمون مستقل انجام شود؛ آموزش/تنظیم و آزمون را جدا کنید.\n\n۷) خروجی برنامه نباید برای تصمیم درمانی استفاده شود. هدف این ماژول آزمون ادعاهاست، نه تشخیص بیمار.")
            .setPositiveButton("باشه", null).show()
    }
}
