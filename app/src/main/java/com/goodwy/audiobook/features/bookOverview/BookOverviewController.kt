package com.goodwy.audiobook.features.bookOverview

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.MenuItem
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bluelinelabs.conductor.RouterTransaction
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import de.paulwoitaschek.flowpref.Pref
import com.goodwy.audiobook.R
import com.goodwy.audiobook.common.pref.PrefKeys
import com.goodwy.audiobook.data.Book
import com.goodwy.audiobook.data.markForPosition
import com.goodwy.audiobook.data.repo.BookRepository
import com.goodwy.audiobook.databinding.BookOverviewBinding
import com.goodwy.audiobook.features.GalleryPicker
import com.goodwy.audiobook.features.ViewBindingController
import com.goodwy.audiobook.features.bookCategory.BookCategoryController
import com.goodwy.audiobook.features.bookOverview.list.BookOverviewAdapter
import com.goodwy.audiobook.features.bookOverview.list.BookOverviewClick
import com.goodwy.audiobook.features.bookOverview.list.BookOverviewHeaderModel
import com.goodwy.audiobook.features.bookOverview.list.BookOverviewItemDecoration
import com.goodwy.audiobook.features.bookPlaying.BookPlayController
import com.goodwy.audiobook.features.folderOverview.FolderOverviewController
import com.goodwy.audiobook.features.imagepicker.CoverFromInternetController
import com.goodwy.audiobook.features.settings.SettingsController
import com.goodwy.audiobook.injection.appComponent
import com.goodwy.audiobook.misc.conductor.asTransaction
import com.goodwy.audiobook.misc.conductor.clearAfterDestroyView
import com.goodwy.audiobook.misc.conductor.clearAfterDestroyViewNullable
import com.goodwy.audiobook.misc.conductor.context
import com.goodwy.audiobook.misc.postedIfComputingLayout
import com.goodwy.audiobook.uitools.BookChangeHandler
import com.goodwy.audiobook.uitools.PlayPauseDrawableSetter
import com.goodwy.audiobook.uitools.PlayPauseColorDrawableSetter
import com.jaredrummler.cyanea.app.BaseCyaneaActivity
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Showing the shelf of all the available books and provide a navigation to each book.
 */
class BookOverviewController : ViewBindingController<BookOverviewBinding>(BookOverviewBinding::inflate),
  EditCoverDialogController.Callback, EditBookBottomSheetController.Callback,
  CoverFromInternetController.Callback,
  BaseCyaneaActivity {

  init {
    appComponent.inject(this)
  }

  @field:[Inject Named(PrefKeys.CURRENT_BOOK)]
  lateinit var currentBookIdPref: Pref<UUID>

  @field:[Inject Named(PrefKeys.GRID_AUTO)]
  lateinit var gridViewAutoPref: Pref<Boolean>

  @field:[Inject Named(PrefKeys.ICON_MODE)]
  lateinit var iconModePref: Pref<Boolean>

  @field:[Inject Named(PrefKeys.PADDING)]
  lateinit var paddingPref: Pref<String>

  @Inject
  lateinit var viewModel: BookOverviewViewModel

  @Inject
  lateinit var galleryPicker: GalleryPicker

  @Inject
  lateinit var repo: BookRepository

  private var playPauseDrawableSetter: PlayPauseDrawableSetter by clearAfterDestroyView()
  private var playPauseColorDrawableSetter: PlayPauseColorDrawableSetter by clearAfterDestroyView()
  private var adapter: BookOverviewAdapter by clearAfterDestroyView()
  private var currentTapTarget by clearAfterDestroyViewNullable<TapTargetView>()
  private var useGrid = false

  override fun BookOverviewBinding.onBindingCreated() {
    setupToolbar()
    setupFab()
    setupMiniPlayerFab()
    setupRecyclerView()
    lifecycleScope.launch {
      viewModel.coverChanged.collect {
        ensureActive()
        bookCoverChanged(it)
      }
    }
    miniPlayer.setOnClickListener {
      val bookid = currentBookIdPref.value
      val book = repo.bookById(bookid)
      book?.let { it1 -> invokeBookSelectionCallback(it1) }
    }
    settingsButton.setOnClickListener {
      val transaction = SettingsController().asTransaction()
      router.pushController(transaction)
    }
    libraryButton.setOnClickListener {
      toFolderOverview()
    }
  }

  private fun BookOverviewBinding.setupFab() {
    binding.fab.setOnClickListener { viewModel.playPause() }
    playPauseDrawableSetter = PlayPauseDrawableSetter(fab)
  }

  private fun BookOverviewBinding.setupMiniPlayerFab() {
    binding.miniPlayerFab.setOnClickListener { viewModel.playPause() }
    playPauseColorDrawableSetter = PlayPauseColorDrawableSetter(miniPlayerFab)
  }

  private fun BookOverviewBinding.setupRecyclerView() {
    recyclerView.setHasFixedSize(true)
    adapter = BookOverviewAdapter(
      bookClickListener = { book, clickType ->
        when (clickType) {
          BookOverviewClick.REGULAR -> invokeBookSelectionCallback(book)
          BookOverviewClick.MENU -> {
            EditBookBottomSheetController(this@BookOverviewController, book).showDialog(router)
          }
        }
      },
      openCategoryListener = { category ->
        Timber.i("open $category")
        router.pushController(BookCategoryController(category).asTransaction())
      }
    )
    recyclerView.adapter = adapter
    // without this the item would blink on every change
    val anim = recyclerView.itemAnimator as SimpleItemAnimator
    anim.supportsChangeAnimations = false
    val layoutManager = GridLayoutManager(activity, 1).apply {
      spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
          if (position == -1) {
            return 1
          }
          val isHeader = adapter.itemAtPositionIsHeader(position)
          return if (isHeader) spanCount else 1
        }
      }
    }
    val listDecoration = BookOverviewItemDecoration(activity!!, layoutManager)
    recyclerView.addItemDecoration(listDecoration)
    recyclerView.layoutManager = layoutManager
  }

  private fun BookOverviewBinding.setupToolbar() {
    toolbar.setOnMenuItemClickListener {
      when (it.itemId) {
        R.id.action_settings -> {
          val transaction = SettingsController().asTransaction()
          router.pushController(transaction)
          true
        }
        R.id.library -> {
          toFolderOverview()
          true
        }
        R.id.toggleGrid -> {
          viewModel.useGrid(!useGrid)
          true
        }
        else -> false
      }
    }
  }

  private fun BookOverviewBinding.gridMenuItem(): GridMenuItem = GridMenuItem(toolbar.menu.findItem(R.id.toggleGrid))
  private fun BookOverviewBinding.settingMenuItem(): SettingMenuItem = SettingMenuItem(toolbar.menu.findItem(R.id.action_settings))
  private fun BookOverviewBinding.libraryMenuItem(): LibraryMenuItem = LibraryMenuItem(toolbar.menu.findItem(R.id.library))

  private fun toFolderOverview() {
    val controller = FolderOverviewController()
    router.pushController(controller.asTransaction())
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    val arguments = galleryPicker.parse(requestCode, resultCode, data)
    if (arguments != null) {
      EditCoverDialogController(this, arguments).showDialog(router)
    }
  }

  private fun invokeBookSelectionCallback(book: Book) {
    // анимация перехода
    currentBookIdPref.value = book.id
    val transaction = RouterTransaction.with(BookPlayController(book.id))
    val transition = BookChangeHandler()
    transition.transitionName = book.coverTransitionName
    transaction.pushChangeHandler(transition)
      .popChangeHandler(transition)
    router.pushController(transaction)
    //router.pushController(BookPlayController(book.id).asTransaction())
  }

  private fun BookOverviewBinding.render(state: BookOverviewState, gridMenuItem: GridMenuItem, settingMenuItem: SettingMenuItem, libraryMenuItem: LibraryMenuItem) {
    Timber.i("render ${state.javaClass.simpleName}")
    /*if (tintNavBar.value) {
      activity!!.window.navigationBarColor = cyanea.navigationBar
      cyanea.edit {
        shouldTintNavBar(true)
      }
    } else {
      cyanea.edit {
        shouldTintNavBar(false)
      }
    }
    activity!!.window.statusBarColor = cyanea.primaryDark*/
    val adapterContent = when (state) {
      is BookOverviewState.Content -> buildList {
          state.categoriesWithContents.forEach { (category, content) ->
            add(BookOverviewHeaderModel(category, content.hasMore))
            addAll(content.books)
          }
        }
      BookOverviewState.Loading, BookOverviewState.NoFolderSet -> emptyList()
    }
    adapter.submitList(adapterContent)

    when (state) {
      is BookOverviewState.Content -> {
        hideNoFolderWarning()
        fab.isVisible = state.miniPlayerStylePref == 1 && state.currentBookPresent

        if (state.miniPlayerStylePref == 2 && state.currentBookPresent) {
          miniPlayer.isVisible = true
          miniPlayerFab.transitionName = activity!!.getString(R.string.fab_transition)
          fab.transitionName = null
        } else {
          miniPlayer.isVisible = false
          miniPlayerFab.transitionName = null
          fab.transitionName = activity!!.getString(R.string.fab_transition)
        }

        useGrid = state.useGrid
        val lm = recyclerView.layoutManager as GridLayoutManager
        lm.spanCount = state.columnCount

        showPlaying(state.playing)
        gridMenuItem.item.apply {
          val useGrid = state.useGrid
          setTitle(if (useGrid) R.string.layout_list else R.string.layout_grid)
          val drawableRes = if (useGrid) R.drawable.ic_view_list else R.drawable.ic_view_grid
          setIcon(drawableRes)
/**Оригинал*/
          /**setTitle(if (useGrid) R.string.layout_list else R.string.layout_grid)
          val drawableRes = if (useGrid) R.drawable.ic_view_list else R.drawable.ic_view_grid
          setIcon(drawableRes)*/
/**Текст вместо значков*/
          /**setTitle(if (useGrid) R.string.grid else R.string.list)*/
        }
        /* Заменяем текст на иконки
        if (showMiniPlayerPref.value) {
          settingMenuItem.item.apply {
            val drawableRes = R.drawable.ic_settings
            setIcon(drawableRes)
          }
          libraryMenuItem.item.apply {
            val drawableRes = R.drawable.audiobook
            setIcon(drawableRes)
          }
        }*/
      }
      BookOverviewState.Loading -> {
        hideNoFolderWarning()
        fab.isVisible = true
        miniPlayer.isVisible = true
      }
      BookOverviewState.NoFolderSet -> {
        showNoFolderWarning()
        fab.isVisible = false
        miniPlayer.isVisible = false
      }
    }

    loadingProgress.isVisible = state == BookOverviewState.Loading
    gridMenuItem.item.isVisible = !gridViewAutoPref.value
    //*Оригинал*/
    //*gridMenuItem.item.isVisible = state != BookOverviewState.Loading*/
    /**Скрыть иконки изменения вида*/
    /**gridMenuItem.item.isVisible = false*/
    settingMenuItem.item.isVisible = false
    libraryMenuItem.item.isVisible = false
    val bookid = currentBookIdPref.value
    val book = repo.bookById(bookid)
    if (book == null) {
      Timber.e("book is null. Return early")
    }
    val currentBookName = book?.name
    val currentMark = book?.content?.currentChapter?.markForPosition(book.content.positionInChapter)
    val currentChapterName = currentMark?.name
    if (currentBookName != null && currentChapterName != null) {
      miniPlayerTitle.text = currentBookName.toString()
      miniPlayerSummary.text = currentChapterName.toString()
    }

    if (iconModePref.value) {
      val density = context.resources.displayMetrics.density
      val width_52dp = (52 * density + 0.5f).toInt()

      val layoutParamsSettings = settingsButton.layoutParams
      layoutParamsSettings?.height = width_52dp
      layoutParamsSettings?.width = width_52dp //ViewGroup.LayoutParams.MATCH_PARENT
      settingsButton.layoutParams = layoutParamsSettings

      val layoutParamsLibrary = libraryButton.layoutParams
      layoutParamsLibrary?.height = width_52dp
      layoutParamsLibrary?.width = width_52dp //ViewGroup.LayoutParams.MATCH_PARENT
      libraryButton.layoutParams = layoutParamsLibrary

      settingsButton.foreground = getDrawable(context, R.drawable.ic_setting)
      settingsButton.foregroundGravity = Gravity.CENTER
      settingsButton.text = ""
      libraryButton.foreground = getDrawable(context, R.drawable.ic_folder_24dp)
      libraryButton.foregroundGravity = Gravity.CENTER
      libraryButton.text = ""
      //settingsButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_setting, 0, 0, 0)
      //libraryButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.image_plus, 0, 0, 0)
    }
  }

  private fun showPlaying(playing: Boolean) {
    Timber.i("Called showPlaying $playing")
    playPauseDrawableSetter.setPlaying(playing = playing)
    playPauseColorDrawableSetter.setPlaying(playing = playing)
  }

  private fun hideNoFolderWarning() {
    val currentTapTarget = currentTapTarget ?: return
    if (currentTapTarget.isVisible) {
      currentTapTarget.dismiss(false)
    }
    this.currentTapTarget = null
  }

  /** Show a warning that no audiobook folder was chosen https://github.com/KeepSafe/TapTargetView*/
  private fun BookOverviewBinding.showNoFolderWarning() {
    if (currentTapTarget != null) {
      return
    }

    val target = TapTarget
      .forView(
        binding.libraryButton,
        activity!!.getString(R.string.onboarding_title),
        activity!!.getString(R.string.onboarding_content)
      )
      .cancelable(true)
      .tintTarget(false)
      .outerCircleColor(R.color.progressColor)
      .descriptionTextColorInt(Color.WHITE)
      .textColorInt(Color.BLACK)
      .targetCircleColorInt(Color.BLACK)
      .transparentTarget(true)
    currentTapTarget = TapTargetView.showFor(activity, target, object : TapTargetView.Listener() {
      override fun onTargetClick(view: TapTargetView?) {
        super.onTargetClick(view)
        toFolderOverview()
      }
    })
  }

  private fun BookOverviewBinding.bookCoverChanged(bookId: UUID) {
    // there is an issue where notifyDataSetChanges throws:
    // java.lang.IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
    recyclerView.postedIfComputingLayout {
      adapter.reloadBookCover(bookId)
    }
  }

  override fun onBookCoverChanged(bookId: UUID) {
    binding.recyclerView.postedIfComputingLayout {
      adapter.reloadBookCover(bookId)
    }
  }

  override fun onInternetCoverRequested(book: Book) {
    router.pushController(CoverFromInternetController(book.id, this).asTransaction())
  }

  override fun onFileCoverRequested(book: Book) {
    galleryPicker.pick(book.id, this)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding.recyclerView.adapter = null
  }

  override fun BookOverviewBinding.onAttach() {
    viewModel.attach()
    val gridMenuItem = gridMenuItem()
    val settingMenuItem = settingMenuItem()
    val libraryMenuItem = libraryMenuItem()
    lifecycleScope.launch {
      viewModel.state()
        .collect {
          render(it, gridMenuItem, settingMenuItem, libraryMenuItem)
        }
    }
    //padding for Edge-to-edge
    lifecycleScope.launch {
      paddingPref.flow.collect {
        val top = paddingPref.value.substringBefore(';').toInt()
        val bottom = paddingPref.value.substringAfter(';').substringBefore(';').toInt()
        val left = paddingPref.value.substringBeforeLast(';').substringAfterLast(';').toInt()
        val right = paddingPref.value.substringAfterLast(';').toInt()
        root.setPadding(left, top, right, bottom)
      }
    }
  }

  override fun onFileDeletionRequested(book: Book) {
    GlobalScope.launch (Dispatchers.IO) {
      val bookContent = book.content
      val currentFile = bookContent.currentFile
      currentFile.delete()
      //repo.hideBook(book.id)
    }
  }
}

private inline class GridMenuItem(val item: MenuItem)
private inline class SettingMenuItem(val item: MenuItem)
private inline class LibraryMenuItem(val item: MenuItem)
