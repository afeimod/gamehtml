package com.flashbox.app.player

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.flashbox.app.FlashBoxApp
import com.flashbox.app.R
import com.flashbox.app.databinding.DialogEngineSettingsBinding
import com.flashbox.app.engine.EngineConfig
import com.flashbox.app.engine.EngineType

/**
 * Per-engine visual settings dialog: quality, aspect (scale), renderer,
 * letterbox, smoothing. Changes apply on next load.
 */
class EngineSettingsDialog : DialogFragment() {

    private var engine: EngineType = EngineType.RUFFLE
    private var config: EngineConfig = EngineConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = EngineType.fromId(arguments?.getString(ARG_ENGINE))
        config = (requireActivity().application as FlashBoxApp).settings.engineConfig(engine)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogEngineSettingsBinding.inflate(layoutInflater)
        val app = requireActivity().application as FlashBoxApp

        // engine selector
        binding.toggleEngineCfg.check(if (engine == EngineType.WAFLASH) R.id.cfg_waflash else R.id.cfg_ruffle)
        binding.cfgRuffle.setOnClickListener { engine = EngineType.RUFFLE; config = app.settings.engineConfig(engine); refresh(binding) }
        binding.cfgWaflash.setOnClickListener { engine = EngineType.WAFLASH; config = app.settings.engineConfig(engine); refresh(binding) }

        // quality
        val qualityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, EngineConfig.QUALITY_OPTIONS)
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerQuality.adapter = qualityAdapter
        binding.spinnerQuality.setSelection(EngineConfig.QUALITY_OPTIONS.indexOf(config.quality).coerceAtLeast(0))

        // aspect / scale
        val scaleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, EngineConfig.SCALE_OPTIONS)
        scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAspect.adapter = scaleAdapter
        binding.spinnerAspect.setSelection(EngineConfig.SCALE_OPTIONS.indexOf(config.scale).coerceAtLeast(0))

        // renderer
        val rendererAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, EngineConfig.RENDERER_OPTIONS)
        rendererAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRenderer.adapter = rendererAdapter
        binding.spinnerRenderer.setSelection(EngineConfig.RENDERER_OPTIONS.indexOf(config.renderer).coerceAtLeast(0))

        binding.switchLetterbox.isChecked = config.letterbox
        binding.switchSmooth.isChecked = config.smooth

        return AlertDialog.Builder(requireContext(), R.style.Theme_FlashBox_Dialog)
            .setTitle(R.string.settings_engine)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newCfg = EngineConfig(
                    quality = binding.spinnerQuality.selectedItem as String,
                    scale = binding.spinnerAspect.selectedItem as String,
                    renderer = binding.spinnerRenderer.selectedItem as String,
                    letterbox = binding.switchLetterbox.isChecked,
                    smooth = binding.switchSmooth.isChecked,
                    frameRate = config.frameRate
                )
                app.settings.setEngineConfig(engine, newCfg)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun refresh(binding: DialogEngineSettingsBinding) {
        binding.spinnerQuality.setSelection(EngineConfig.QUALITY_OPTIONS.indexOf(config.quality).coerceAtLeast(0))
        binding.spinnerAspect.setSelection(EngineConfig.SCALE_OPTIONS.indexOf(config.scale).coerceAtLeast(0))
        binding.spinnerRenderer.setSelection(EngineConfig.RENDERER_OPTIONS.indexOf(config.renderer).coerceAtLeast(0))
        binding.switchLetterbox.isChecked = config.letterbox
        binding.switchSmooth.isChecked = config.smooth
    }

    companion object {
        private const val ARG_ENGINE = "engine"
        fun newInstance(engine: EngineType): EngineSettingsDialog {
            return EngineSettingsDialog().apply {
                arguments = Bundle().apply { putString(ARG_ENGINE, engine.id) }
            }
        }
    }
}
