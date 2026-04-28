package com.celltowerid.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.celltowerid.android.R
import com.celltowerid.android.databinding.ActivityOnboardingBinding
import com.celltowerid.android.util.Preferences

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private data class Page(val iconRes: Int, val titleRes: Int, val descRes: Int)

    private val pages = listOf(
        Page(android.R.drawable.ic_dialog_map, R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc),
        Page(android.R.drawable.ic_lock_idle_lock, R.string.onboarding_security_title, R.string.onboarding_security_desc),
        Page(android.R.drawable.ic_secure, R.string.onboarding_privacy_title, R.string.onboarding_privacy_desc),
        Page(android.R.drawable.ic_menu_mylocation, R.string.onboarding_permissions_title, R.string.onboarding_permissions_desc),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = PageAdapter()
        setupDots()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                val isLast = position == pages.size - 1
                binding.btnNext.text = getString(
                    if (isLast) R.string.onboarding_done else R.string.onboarding_next
                )
                binding.btnSkip.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
            }
        })

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages.size - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener { finishOnboarding() }
    }

    private fun finishOnboarding() {
        Preferences(this).onboardingComplete = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setupDots() {
        val container = binding.dotsContainer
        for (i in pages.indices) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(12, 12).apply {
                    marginStart = 8
                    marginEnd = 8
                }
                setBackgroundResource(android.R.drawable.presence_invisible)
            }
            container.addView(dot)
        }
        updateDots(0)
    }

    private fun updateDots(selected: Int) {
        val container = binding.dotsContainer
        for (i in 0 until container.childCount) {
            container.getChildAt(i).alpha = if (i == selected) 1.0f else 0.3f
        }
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {
        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.icon)
            val title: TextView = view.findViewById(R.id.title)
            val description: TextView = view.findViewById(R.id.description)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val page = pages[position]
            holder.icon.setImageResource(page.iconRes)
            holder.title.setText(page.titleRes)
            holder.description.setText(page.descRes)
        }

        override fun getItemCount() = pages.size
    }
}
