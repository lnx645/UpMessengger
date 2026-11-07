package com.upme.messenger.presentation.avatar

import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.upme.messenger.R
import com.upme.messenger.presentation.common.AppToast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Fragment khusus crop avatar — keren, WA-style, Manrope, tanpa lib eksternal.
 * Usage: UserAvatarCroppingFragment.newInstance(uri).show via parentFragmentManager.replace
 * Result: setFragmentResult("avatar_crop", bundleOf("uri" to croppedUriString))
 */
class UserAvatarCroppingFragment : Fragment() {

    private lateinit var img: ImageView
    private lateinit var overlay: CropOverlayView
    private var sourceUri: Uri? = null
    private var sourceBmp: Bitmap? = null

    // Matrix gesture state
    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private var startPt = PointF()
    private var midPt = PointF()
    private var mode = MODE_NONE
    private var oldDist = 1f
    private var lastDegree = 0f

    companion object {
        const val REQUEST_KEY = "avatar_crop"
        const val KEY_URI = "uri"
        const val MODE_NONE = 0
        const val MODE_DRAG = 1
        const val MODE_ZOOM = 2
        fun newInstance(uri: Uri): UserAvatarCroppingFragment {
            return UserAvatarCroppingFragment().apply {
                arguments = Bundle().apply { putParcelable("src", uri) }
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_user_avatar_cropping, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        sourceUri = arguments?.getParcelable("src")
            ?: arguments?.getString("src")?.let { Uri.parse(it) }
        img = v.findViewById(R.id.imgCrop)
        overlay = v.findViewById(R.id.overlay)

        // load bitmap
        try {
            val uri = sourceUri ?: run { AppToast.show(requireContext(), "Foto tidak ditemukan"); parentFragmentManager.popBackStack(); return }
            val stream = requireContext().contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bmp == null) { AppToast.show(requireContext(), "Gagal memuat foto"); parentFragmentManager.popBackStack(); return }
            sourceBmp = bmp
            img.setImageBitmap(bmp)
            img.imageMatrix = matrix
            img.scaleType = ImageView.ScaleType.MATRIX
            // center & fit
            v.post { centerImage() }
        } catch (e: Exception) {
            AppToast.show(requireContext(), "Error: ${e.message}")
            parentFragmentManager.popBackStack()
        }

        setupGestures()

        v.findViewById<View>(R.id.btnCloseCrop).setOnClickListener { parentFragmentManager.popBackStack() }
        v.findViewById<View>(R.id.btnBatal).setOnClickListener { parentFragmentManager.popBackStack() }
        v.findViewById<View>(R.id.btnRotate).setOnClickListener { rotate90() }
        v.findViewById<View>(R.id.btnSimpan).setOnClickListener { doCropAndReturn() }
    }

    private fun centerImage() {
        val bmp = sourceBmp ?: return
        val vw = img.width.toFloat(); val vh = img.height.toFloat()
        if (vw == 0f || vh == 0f) return
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        // scale to cover circle (like centerCrop)
        val scale = max(vw / bw, vh / bh) * 1.1f
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate((vw - bw * scale) / 2f, (vh - bh * scale) / 2f)
        img.imageMatrix = matrix
    }

    private fun rotate90() {
        val bmp = sourceBmp ?: return
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(90f) }, true)
        sourceBmp = rotated
        img.setImageBitmap(rotated)
        centerImage()
        AppToast.show(requireContext(), "Diputar 90°")
    }

    private fun setupGestures() {
        img.setOnTouchListener { _, event ->
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    startPt.set(event.x, event.y)
                    mode = MODE_DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(midPt, event)
                        mode = MODE_ZOOM
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == MODE_DRAG) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(event.x - startPt.x, event.y - startPt.y)
                    } else if (mode == MODE_ZOOM) {
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            matrix.set(savedMatrix)
                            val scale = newDist / oldDist
                            matrix.postScale(scale, scale, midPt.x, midPt.y)
                            // clamp scale 0.5x ~ 4x
                            val vals = FloatArray(9); matrix.getValues(vals)
                            val curScale = vals[Matrix.MSCALE_X]
                            if (curScale < 0.5f) matrix.set(savedMatrix)
                            if (curScale > 5f) matrix.set(savedMatrix)
                        }
                    }
                    img.imageMatrix = matrix
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> mode = MODE_NONE
            }
            true
        }
    }

    private fun doCropAndReturn() {
        val bmp = sourceBmp ?: return
        val viewBmp = getViewBitmap() ?: run { AppToast.show(requireContext(), "Gagal crop"); return }
        // crop square from center based on overlay circle
        val size = min(viewBmp.width, viewBmp.height)
        val cropSize = (size * 0.82f).toInt()
        val x = (viewBmp.width - cropSize) / 2
        val y = (viewBmp.height - cropSize) / 2
        val cropped = try {
            Bitmap.createBitmap(viewBmp, x.coerceAtLeast(0), y.coerceAtLeast(0), cropSize.coerceAtMost(viewBmp.width - x), cropSize.coerceAtMost(viewBmp.height - y))
        } catch (e: Exception) { viewBmp }
        // circular mask (optional square with rounded, we keep square for storage but UI shows circle)
        val out = Bitmap.createScaledBitmap(cropped, 512, 512, true)
        // save to cache
        try {
            val file = File(requireContext().cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val uri = Uri.fromFile(file)
            // also persist to internal files for profile
            val persist = File(requireContext().filesDir, "avatar.jpg")
            file.copyTo(persist, overwrite = true)
            setFragmentResult(REQUEST_KEY, bundleOf(KEY_URI to uri.toString()))
            AppToast.show(requireContext(), "Foto disimpan")
            parentFragmentManager.popBackStack()
        } catch (e: Exception) {
            AppToast.show(requireContext(), "Gagal simpan: ${e.message}")
        }
    }

    private fun getViewBitmap(): Bitmap? {
        // render the ImageView with current matrix into bitmap matching its size
        val w = img.width; val h = img.height
        if (w == 0 || h == 0) return null
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        // draw image with matrix
        val drawable = img.drawable ?: return null
        c.save()
        c.concat(matrix)
        drawable.draw(c)
        c.restore()
        return b
    }

    private fun spacing(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val x = e.getX(0) - e.getX(1); val y = e.getY(0) - e.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }
    private fun midPoint(p: PointF, e: MotionEvent) {
        p.set((e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f)
    }
}
