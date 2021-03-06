import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter
import javax.swing.JPanel

class VehicleFileFilter < FileFilter
  def getDescription
    'Vehicle Images'
  end

  def accept(file)
    %w(.png .srf).include? File.extname(file.name).downcase
  end
end

class AnimationPanel < JPanel
  import javax.imageio.ImageIO
  
  attr_accessor :interval
  
  def initialize(options = {})
    super()
    @interval = options[:interval] || 0.12
    @images = nil
    @cur_image = nil
    @cur_spot = nil
    @thread = nil
  end

  def paintComponent(graphics)
    width = getWidth()
    height = getHeight()
    bounds = graphics.getClipBounds()
    if is_background_set
      graphics.set_color get_background
      graphics.fillRect(0, 0, width, height)
    end
    graphics.draw_image(@cur_image, (width - @cur_image.width) / 2, (height - @cur_image.height) / 2, nil) if @cur_image
  end

  def set_images(images)
    @images = images
    @cur_spot = 0
    @cur_image = @images[@cur_spot]
    start
  end
  
  def next_image
    @cur_spot = (@cur_spot + 1) % @images.length
    @cur_image = @images[@cur_spot]
  end

  def start
    stop if @thread
    return unless @images
    @thread = Thread.new { while true; repaint; sleep @interval; next_image; end }
  end

  def stop
    return unless @thread
    @thread.kill
    @thread = nil
  end

  def clear
    stop
    @images = nil
    @cur_image = nil
    repaint
  end
end

class ViewBuilder
  import javax.swing.JFrame
  import javax.swing.JLabel
  import java.awt.Dimension
  import javax.swing.JMenuBar
  import javax.swing.JMenu
  import javax.swing.JMenuItem
  import java.awt.event.KeyEvent
  import java.awt.event.ActionListener
  import java.awt.Color
  import javax.swing.BoxLayout
  
  include ActionListener

  attr_accessor :animation_panel, :settings_panel, :frame

  def initialize
    @callbacks = {}
  end

  def actionPerformed(e)
    case e.source
    when @open_menu_item
      @callbacks[:open].call(e) if @callbacks[:open]
    when @exit_menu_item
      @callbacks[:exit].call(e) if @callbacks[:exit]
    end
  end
  
  def set_callback(key, proc)
    @callbacks[key] = proc
  end
  
  def build
    @frame = JFrame.new('SRF Converter')
    container_panel = JPanel.new
    container_panel.set_layout BoxLayout.new(container_panel, BoxLayout::X_AXIS)
    @animation_panel = AnimationPanel.new
    @settings_panel = JPanel.new
    
    @settings_panel.set_background Color.new(0xffcccc)
    @settings_panel.minimum_size = Dimension.new 150, 300
    @settings_panel.maximum_size = Dimension.new 150, 300
    @animation_panel.set_background Color.new(0xccffcc)
    @animation_panel.minimum_size = Dimension.new 250, 300
    @animation_panel.maximum_size = Dimension.new 250, 300

    @file_chooser = JFileChooser.new
    @file_chooser.addChoosableFileFilter VehicleFileFilter.new

    menu_bar = JMenuBar.new
    file_menu = JMenu.new('File')
    
    @open_menu_item = JMenuItem.new('Open')
    @open_menu_item.add_action_listener self
    @exit_menu_item = JMenuItem.new('Exit')
    @exit_menu_item.add_action_listener self
    
    file_menu.add(@open_menu_item)
    file_menu.add(@exit_menu_item)
    menu_bar.add(file_menu)
    @frame.setJMenuBar(menu_bar)
    
    @frame.add container_panel
    container_panel.add @settings_panel
    container_panel.add @animation_panel

    @frame.size = Dimension.new 400, 300
    @frame.resizable = false
    @frame.default_close_operation = JFrame::EXIT_ON_CLOSE

    @frame
  end
  
  def get_file_to_open
    result = @file_chooser.show_open_dialog(@frame)
    (result == JFileChooser::APPROVE_OPTION) ? @file_chooser.get_selected_file.get_path : nil
  end
end
