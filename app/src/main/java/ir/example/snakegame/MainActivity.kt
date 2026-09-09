package ir.example.snakegame
 
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
 
class MainActivity : AppCompatActivity(), SnakeView.UiHost {
 
    private lateinit var overlay: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var errorText: TextView
    private var onSubmitCallback: ((String) -> Unit)? = null
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
 
        val root = FrameLayout(this)
 
        // Build the name-input overlay FIRST. SnakeView's constructor can call
        // showNameInput() immediately (for a first-time player), so overlay,
        // nameInput and errorText must already exist before SnakeView is created.
        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#DD101418"))
            visibility = View.GONE
            setPadding(80, 0, 80, 0)
        }
        val title = TextView(this).apply {
            text = "اسمت رو بنویس"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        nameInput = EditText(this).apply {
            hint = "اسم بازیکن"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        errorText = TextView(this).apply {
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        val submitBtn = Button(this).apply {
            text = "ثبت اسم"
            setOnClickListener {
                onSubmitCallback?.invoke(nameInput.text.toString().trim())
            }
        }
        overlay.addView(title)
        overlay.addView(nameInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 40 })
        overlay.addView(errorText)
        overlay.addView(submitBtn)
 
        // Now it's safe to construct SnakeView.
        val snakeView = SnakeView(this, this)
        root.addView(snakeView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
 
        setContentView(root)
    }
 
    override fun showNameInput(error: String?, onSubmit: (String) -> Unit) {
        onSubmitCallback = onSubmit
        errorText.text = error ?: ""
        overlay.visibility = View.VISIBLE
    }
 
    override fun hideNameInput() {
        overlay.visibility = View.GONE
        nameInput.setText("")
    }
}
 
