package com.celltowerid.android.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.celltowerid.android.R
import com.celltowerid.android.databinding.DialogAddTowerBinding
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.ui.viewmodel.MapViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddTowerDialogFragment : DialogFragment() {

    private var _binding: DialogAddTowerBinding? = null
    private val binding get() = requireNotNull(_binding) { "binding accessed after onDestroyView" }

    // Share the MapViewModel with the host MapFragment so adding a tower
    // refreshes the same towers list the map renders from.
    private val mapViewModel: MapViewModel by viewModels({ requireParentFragment() })

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        _binding = DialogAddTowerBinding.inflate(inflater)

        setupRadioSpinner(binding.spinnerRadio)
        prefillCoordinates()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_tower_title)
            .setView(binding.root)
            .setPositiveButton(R.string.add_tower_save, null) // overridden below to suppress dismiss on validation error
            .setNegativeButton(R.string.add_tower_cancel) { d, _ -> d.dismiss() }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    positive.setOnClickListener { onSaveClicked() }
                }
            }
    }

    private fun setupRadioSpinner(spinner: Spinner) {
        val labels = SUPPORTED_RADIOS.map { it.name }
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
    }

    private fun prefillCoordinates() {
        val args = arguments ?: return
        if (args.containsKey(ARG_LAT)) {
            binding.editLatitude.setText(args.getDouble(ARG_LAT).toString())
        }
        if (args.containsKey(ARG_LON)) {
            binding.editLongitude.setText(args.getDouble(ARG_LON).toString())
        }
    }

    private fun onSaveClicked() {
        clearFieldErrors()

        val radio = SUPPORTED_RADIOS[binding.spinnerRadio.selectedItemPosition]
        val result = ManualTowerInput.parse(
            radio = radio,
            mcc = binding.editMcc.text?.toString().orEmpty(),
            mnc = binding.editMnc.text?.toString().orEmpty(),
            tacLac = binding.editTacLac.text?.toString().orEmpty(),
            cid = binding.editCid.text?.toString().orEmpty(),
            latitude = binding.editLatitude.text?.toString().orEmpty(),
            longitude = binding.editLongitude.text?.toString().orEmpty(),
            pci = binding.editPci.text?.toString()
        )

        when (result) {
            is ManualTowerInput.Result.Invalid -> showFieldErrors(result.errors)
            is ManualTowerInput.Result.Valid -> {
                mapViewModel.addManualTower(
                    radio = result.radio,
                    mcc = result.mcc,
                    mnc = result.mnc,
                    tacLac = result.tacLac,
                    cid = result.cid,
                    latitude = result.latitude,
                    longitude = result.longitude,
                    pci = result.pci
                )
                dismiss()
            }
        }
    }

    private fun clearFieldErrors() {
        binding.tilMcc.error = null
        binding.tilMnc.error = null
        binding.tilTacLac.error = null
        binding.tilCid.error = null
        binding.tilLatitude.error = null
        binding.tilLongitude.error = null
        binding.tilPci.error = null
    }

    private fun showFieldErrors(errors: Map<ManualTowerInput.Field, String>) {
        errors[ManualTowerInput.Field.MCC]?.let { binding.tilMcc.error = it }
        errors[ManualTowerInput.Field.MNC]?.let { binding.tilMnc.error = it }
        errors[ManualTowerInput.Field.TAC_LAC]?.let { binding.tilTacLac.error = it }
        errors[ManualTowerInput.Field.CID]?.let { binding.tilCid.error = it }
        errors[ManualTowerInput.Field.LATITUDE]?.let { binding.tilLatitude.error = it }
        errors[ManualTowerInput.Field.LONGITUDE]?.let { binding.tilLongitude.error = it }
        errors[ManualTowerInput.Field.PCI]?.let { binding.tilPci.error = it }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "AddTowerDialog"
        private const val ARG_LAT = "lat"
        private const val ARG_LON = "lon"
        private val SUPPORTED_RADIOS = listOf(
            RadioType.LTE,
            RadioType.NR,
            RadioType.GSM,
            RadioType.WCDMA
        )

        fun newInstance(prefillLat: Double? = null, prefillLon: Double? = null): AddTowerDialogFragment {
            return AddTowerDialogFragment().apply {
                arguments = Bundle().apply {
                    if (prefillLat != null) putDouble(ARG_LAT, prefillLat)
                    if (prefillLon != null) putDouble(ARG_LON, prefillLon)
                }
            }
        }
    }
}
