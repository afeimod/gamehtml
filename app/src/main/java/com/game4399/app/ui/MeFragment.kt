package com.game4399.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.game4399.app.FavoritesActivity
import com.game4399.app.SettingsActivity
import com.game4399.app.databinding.FragmentMeBinding

/**
 * "我的" Tab：收藏 / 历史 / 设置入口。
 */
class MeFragment : Fragment() {

    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardFavorites.setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }
        binding.cardHistory.setOnClickListener {
            val intent = Intent(requireContext(), FavoritesActivity::class.java)
            intent.putExtra(FavoritesActivity.EXTRA_MODE, FavoritesActivity.MODE_HISTORY)
            startActivity(intent)
        }
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
