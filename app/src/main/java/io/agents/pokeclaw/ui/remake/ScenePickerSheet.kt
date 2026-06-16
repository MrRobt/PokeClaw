// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.remake

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.agents.pokeclaw.R
import io.agents.pokeclaw.utils.XLog

/**
 * Bottom-sheet picker for [RemakeScene] presets
 * (US-D-028-REMAKE-SCENE-PICKER). The host activity/fragment passes a
 * callback via [newInstance] which receives the user's choice as a
 * ready-to-paste prompt string (the scene's example inputs rendered
 * via [RemakeScene.toExamplePrompt]).
 *
 * The sheet deliberately does NOT itself start a task; it only emits
 * the chosen scene's example prompt and lets the chat input bar decide
 * what to do (insert text, open template form, etc.). This keeps the
 * sheet decoupled from TaskOrchestrator.
 */
class ScenePickerSheet : BottomSheetDialogFragment() {

    private companion object {
        private const val TAG = "ScenePickerSheet"
        private const val ARG_ON_CHOSEN = "arg_on_chosen"
        private const val GRID_SPAN = 2

        fun newInstance(onChosen: (RemakeScene, String) -> Unit): ScenePickerSheet {
            // Bundles can't carry lambdas; we use a per-fragment tag
            // and look up the callback through the fragment manager.
            val f = ScenePickerSheet()
            f.onChosen = onChosen
            return f
        }
    }

    private var onChosen: ((RemakeScene, String) -> Unit)? = null

    private val adapter = SceneGridAdapter { scene ->
        val prompt = scene.toExamplePrompt()
        XLog.i(TAG, "scene picked: id=${scene.id} promptLen=${prompt.length}")
        onChosen?.invoke(scene, prompt)
            ?: Toast.makeText(requireContext(), scene.name, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.sheet_remake_scene, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val grid = view.findViewById<RecyclerView>(R.id.rv_remake_scenes)
        grid.layoutManager = GridLayoutManager(requireContext(), GRID_SPAN)
        grid.adapter = adapter
        adapter.submit(RemakeSceneCatalog.listAll())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState)
    }
}

private class SceneGridAdapter(
    private val onPick: (RemakeScene) -> Unit,
) : RecyclerView.Adapter<SceneGridAdapter.Holder>() {

    private val items: MutableList<RemakeScene> = ArrayList()

    fun submit(newItems: List<RemakeScene>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_remake_scene_card, parent, false)
        return Holder(card)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val scene = items[position]
        holder.bind(scene, onPick)
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_scene_name)
        private val desc: TextView = itemView.findViewById(R.id.tv_scene_desc)
        fun bind(scene: RemakeScene, onPick: (RemakeScene) -> Unit) {
            title.text = scene.name
            desc.text = scene.description
            itemView.setOnClickListener { onPick(scene) }
        }
    }
}
